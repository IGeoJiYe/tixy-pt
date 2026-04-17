package com.tixypt.chatting.support.read.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.read.dto.event.SupportReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.SupportUnreadSyncEvent;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportReadReceiptService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final MemberService memberService;
    private final SupportEventDispatcher supportEventDispatcher;

    // 읽음 위치를 문의방에 반영한다
    @Transactional
    public void markAsRead(
            Long loginUserId,
            String userName,
            Long roomId,
            Long lastReadMessageId
    ) {
        if (lastReadMessageId == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_READ_RECEIPT);
        }

        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = supportRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        SupportAccessPolicy.validateParticipantWritable(loginUser);
        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        SupportAccessPolicy.validateRoomWritable(room);

        if (SupportAccessPolicy.isCounselor(loginUser)) {
            room.touchCounselorActivity(LocalDateTime.now());
        }

        validateReadMessage(roomId, lastReadMessageId);

        LocalDateTime readAt = LocalDateTime.now();
        boolean updated = SupportAccessPolicy.isCounselor(loginUser)
                ? room.markCounselorRead(lastReadMessageId, readAt)
                : room.markCustomerRead(lastReadMessageId, readAt);

        if (!updated) {
            return;
        }

        long unreadCount = unreadCount(room, loginUser);
        LocalDateTime effectiveReadAt = SupportAccessPolicy.isCounselor(loginUser)
                ? room.getCounselorLastReadAt()
                : room.getCustomerLastReadAt();
        Long effectiveLastReadMessageId = SupportAccessPolicy.isCounselor(loginUser)
                ? room.getCounselorLastReadMessageId()
                : room.getCustomerLastReadMessageId();

        SupportReadReceiptEvent roomEvent = new SupportReadReceiptEvent(
                roomId,
                loginUserId,
                loginUser.getRole().name(),
                effectiveLastReadMessageId,
                effectiveReadAt
        );

        SupportUnreadSyncEvent userEvent = new SupportUnreadSyncEvent(
                roomId,
                effectiveLastReadMessageId,
                unreadCount,
                effectiveReadAt
        );

        supportEventDispatcher.dispatchReadReceiptAfterCommit(userName, roomEvent, userEvent);
    }


    // 읽음 기준 메시지가 실제로 같은 문의방에 속해 잇는지 확인
    private void validateReadMessage(Long roomId, Long lastReadMessageId) {
        if (!supportMessageRepository.existsByIdAndRoomId(lastReadMessageId, roomId)) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_READ_RECEIPT);
        }
    }

    // 현재 사용자가 읽지 않은 메시지 수 다시 계산
    private long unreadCount(SupportRoom room, Member loginUser) {
        if (SupportAccessPolicy.isCounselor(loginUser)) {
            long lastReadMessageId = room.getCounselorLastReadMessageId() == null ? 0L : room.getCounselorLastReadMessageId();
            return supportMessageRepository.countUnreadForCounselor(room.getId(), lastReadMessageId, loginUser.getId());
        }

        long lastReadMessageId = room.getCustomerLastReadMessageId() == null ? 0L : room.getCustomerLastReadMessageId();
        return supportMessageRepository.countUnreadForCustomer(room.getId(), lastReadMessageId, loginUser.getId());
    }
}
