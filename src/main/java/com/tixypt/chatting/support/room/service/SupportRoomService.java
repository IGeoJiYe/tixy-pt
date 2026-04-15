package com.tixypt.chatting.support.room.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.message.service.SupportSystemMessageService;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.dto.response.CreateSupportRoomResponse;
import com.tixypt.chatting.support.room.dto.response.SupportRoomDetailResponse;
import com.tixypt.chatting.support.room.dto.response.SupportRoomSummaryResponse;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.tixypt.chatting.support.policy.SupportAccessPolicy.isCounselor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportRoomService {

    private static final List<SupportRoomStatus> ACTIVE_ROOM_STATUSES = List.of(
            SupportRoomStatus.OPEN,
            SupportRoomStatus.SOLVED
    );

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SupportSystemMessageService supportSystemMessageService;
    private final MemberService memberService;

    // 고객이 현재 이어서 사용할 문의방을 확보
    // 이미 진행 중인 OPEN/SOLVED 방이 있으면 재사용하고 없을 때만 새 방 만듦
    @Transactional
    public CreateSupportRoomResponse createRoom(Long loginUserId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateCustomerOnly(loginUser);

        return supportRoomRepository.findTopByCustomerUserIdAndStatusInOrderByIdDesc(loginUserId, ACTIVE_ROOM_STATUSES)
                .map(existingRoom -> new CreateSupportRoomResponse(existingRoom.getId(), false))
                .orElseGet(() -> creatNewOpenRoom(loginUserId));
    }

    // 같은 "/me" 목록이라도 고객, 상담원, SUPER_ADMIN이 보는 기준이 다르기 때문에 역할에 맞는 조회 쿼리 선택
    public SliceResponse<SupportRoomSummaryResponse> getMyRooms(Long loginUserId, int page, int size) {
        Member loginUser = memberService.findById(loginUserId);
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);

        if (SupportAccessPolicy.isSuperAdmin(loginUser)) {
            return buildRoomSlice(
                    supportRoomRepository.findAllByOrderByIdDesc(pageable),
                    loginUser
            );
        }

        if (isCounselor(loginUser)) {
            return buildRoomSlice(
                    supportRoomRepository.findAssignedRoomsForCounselor(loginUserId, pageable),
                    loginUser
            );
        }

        return buildRoomSlice(
                supportRoomRepository.findRoomsForCustomer(loginUserId, pageable),
                loginUser
        );
    }

    // 방 상세는 고객이랑 상담원 SUPER_ADMIN이 모두 사용할 수 있지만 실제 접근 가능 여부는 다시 검증
    public SupportRoomDetailResponse getRoomDetail(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = getRoomOrThrow(roomId);

        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        return SupportRoomDetailResponse.from(room);
    }



    // 방 생성 시점에는 담당 상담원이 정해지지 않았으니까 counselor는 null로 시작
    private CreateSupportRoomResponse creatNewOpenRoom(Long customerUserId) {
        SupportRoom room = supportRoomRepository.save(SupportRoom.open(customerUserId, null));
        supportSystemMessageService.appendRoomCreatedMessage(room);
        return new CreateSupportRoomResponse(room.getId(), true);
    }

   // 목록 화면에서 바로 읽지 않은 메시지 수를 보여 줄 수 있게 각 방의 unread 개수를 함께 계산해서 변환
    private SliceResponse<SupportRoomSummaryResponse> buildRoomSlice(Slice<SupportRoom> rooms, Member loginUser) {
        Slice<SupportRoomSummaryResponse> responseSlice = rooms
                .map(room -> SupportRoomSummaryResponse.from(room, calculateUnreadCount(room, loginUser)));
        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    private SupportRoom getRoomOrThrow(Long roomId) {
        return supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));
    }


    private long calculateUnreadCount(SupportRoom room, Member loginUser) {
        if (SupportAccessPolicy.isSuperAdmin(loginUser)) {
            return 0L;
        }

        if (SupportAccessPolicy.isCounselor(loginUser)) {
            long lastReadMessageId = room.getCounselorLastReadMessageId() == null ? 0L : room.getCounselorLastReadMessageId();
            return supportMessageRepository.countUnreadForCounselor(room.getId(), lastReadMessageId, loginUser.getId());
        }

        long lastReadMessageId = room.getCustomerLastReadMessageId() == null ? 0L : room.getCustomerLastReadMessageId();
        return supportMessageRepository.countUnreadForCustomer(room.getId(), lastReadMessageId, loginUser.getId());
    }
}
