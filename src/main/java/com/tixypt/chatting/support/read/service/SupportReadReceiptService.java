package com.tixypt.chatting.support.read.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.enums.MemberRole;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
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

    // 읽음 처리랑 unread 재 계산은 항상 같은 기준으로 움직이니까 검증, 상태 반영, 이벤트 payload 생성을 한 묶음
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
        SupportRoom room = supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        SupportAccessPolicy.validateParticipantWritable(loginUser);
        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        validateRoomWritable(room);

        if (isCounselor(loginUser)) {
            // 상담원이 읽음 처리했다면 그 방을 실제로 보고 있는 중으로 간주
            room.touchCounselorActivity(LocalDateTime.now());
        }

        validateReadMessage(roomId, lastReadMessageId);

        LocalDateTime readAt = LocalDateTime.now();
        boolean updated = isCounselor(loginUser)
                ? room.markCounselorRead(lastReadMessageId, readAt)
                : room.markCustomerRead(lastReadMessageId, readAt);

        if (!updated) {
            return;
        }

        long unreadCount = unreadCount(room, loginUser);
        LocalDateTime effectiveReadAt = isCounselor(loginUser)
                ? room.getCounselorLastReadAt()
                : room.getCustomerLastReadAt();
        Long effectiveLastReadMessageId = isCounselor(loginUser)
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

        supportEventDispatcher.dispatchReadReceipt(userName, roomEvent, userEvent);
    }


    // 다른 방 메시지를 읽음 기준점으로 보내는 잘못된 요청을 막기 위해서 lastReadMessageId가 현재 문의방 메시지인지 먼저 검증
    private void validateReadMessage(Long roomId, Long lastReadMessageId) {
        if (!supportMessageRepository.existsByIdAndRoomId(lastReadMessageId, roomId)) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_READ_RECEIPT);
        }
    }

    // unread 기준은 역할마다 다르니까 고객이랑 상담원 각각의 마지막 읽음 위치로 따로 계산
    private long unreadCount(SupportRoom room, Member loginUser) {
        if (isCounselor(loginUser)) {
            long lastReadMessageId = room.getCounselorLastReadMessageId() == null
                    ? 0L
                    : room.getCounselorLastReadMessageId();
            return supportMessageRepository.countUnreadForCounselor(
                    room.getId(),
                    lastReadMessageId,
                    loginUser.getId()
            );
        }

        long lastReadMessageId = room.getCustomerLastReadMessageId() == null
                ? 0L
                : room.getCustomerLastReadMessageId();
        return supportMessageRepository.countUnreadForCustomer(
                room.getId(),
                lastReadMessageId,
                loginUser.getId()
        );
    }

    // 종료된 문의방은 읽음 상태도 더 이상 갱신하지 않도록 막음
    private void validateRoomWritable(SupportRoom room) {
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ALREADY_CLOSED);
        }
    }

    private boolean isCounselor(Member loginUser) {
        return loginUser.getRole() == MemberRole.ADMIN;
    }
}
