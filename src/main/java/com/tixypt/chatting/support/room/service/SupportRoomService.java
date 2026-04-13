package com.tixypt.chatting.support.room.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.enums.MemberRole;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.room.dto.CreateSupportRoomResponse;
import com.tixypt.chatting.support.room.dto.SupportRoomDetailResponse;
import com.tixypt.chatting.support.room.dto.SupportRoomSummaryResponse;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportRoomService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final MemberService memberService;

    @Transactional
    public CreateSupportRoomResponse createRoom(Long loginUserId) {
        // 고객이 지금 이어서 사용할 문의방을 확보. 이미 open 방이 있으면 재사용하고, 없을 때만 새 문의방 만듦
        Member loginUser = memberService.findById(loginUserId);
        validateCustomerOnly(loginUser);

        return supportRoomRepository.findByCustomerUserIdAndStatus(loginUserId, SupportRoomStatus.OPEN)
                .map(existRoom -> new CreateSupportRoomResponse(existRoom.getId(), false))
                .orElseGet(() -> creatNewOpenRoom(loginUserId));
    }

    public SliceResponse<SupportRoomSummaryResponse> getMyRooms(Long loginUserId, int page, int size) {
        // 같은 "/me" 목록이라도 고객이랑 상담원이 보는 기준이 다르니까 역할에 맞는 조회 쿼리만 나눠서 타고 응답 형태는 공통
        Member loginUser = memberService.findById(loginUserId);
        Pageable pageable = PageableUtil.createSafePageableDesc(page, size, "id");

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

    public SupportRoomDetailResponse getRoomDetail(Long loginUserId, Long roomId) {
        // 상세 조회는 공통 서비스에서 처리하되,
        // 실제 접근 가능 여부는 고객/상담원 규칙에 맞춰 다시 검증한다.
        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = getRoomOrThrow(roomId);

        validateRoomAccess(loginUser, room);
        return SupportRoomDetailResponse.from(room);
    }




    private CreateSupportRoomResponse creatNewOpenRoom(Long customerUserId) {
        // 고객 방 생성 시점에는 상담원이 정해지지 않았으니까 counselor는 null로 둔다
        SupportRoom room = supportRoomRepository.save(SupportRoom.open(customerUserId, null));
        return new CreateSupportRoomResponse(room.getId(), true);
    }

    private void validateCustomerOnly(Member loginUser) {
        // 문의방 생성은 고객 시작 흐름으로 제한함, 상담원은 대기열 claim 또는 운영 기능 통해서만 방에 들어감
        if (isCounselor(loginUser)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    private void validateRoomAccess(Member loginUser, SupportRoom room) {
        if (isCounselor(loginUser)) {
            validateCounselorAccess(loginUser.getId(), room);
            return;
        }

        validateCustomerAccess(loginUser.getId(), room);
    }

    private SliceResponse<SupportRoomSummaryResponse> buildRoomSlice(Slice<SupportRoom> rooms, Member loginUser) {
        // 방 요약 목록에서는 각 방의 unread 수를 함께 계산해서 목록 화면만으로 새 메시지 유무를 바로 표현할 수 있게 함
        Slice<SupportRoomSummaryResponse> responseSlice = rooms
                .map(room -> SupportRoomSummaryResponse.from(room, calculateUnreadCount(room, loginUser)));
        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    private SupportRoom getRoomOrThrow(Long roomId) {
        return supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));
    }

    private void validateCustomerAccess(Long loginUserId, SupportRoom room) {
        // 고객 상세/목록 흐름에서 타인의 문의방을 보는 것을 막음
        if (!room.getCustomerUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    private void validateCounselorAccess(Long loginUserId, SupportRoom room) {
        // 상담원은 현재 claim된 방에만 접근할 수 있음 미배정 방이나 다른 상담원 담당 방은 운영 대기열에서만 보여 줌
        if (room.getCounselorUserId() == null || !room.getCounselorUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    private boolean isCounselor(Member loginUser) {
        // 현재는 admin을 상담원 역할
        return loginUser.getRole() == MemberRole.ADMIN;
    }

    private long calculateUnreadCount(SupportRoom room, Member loginUser) {
        // 고객은 상담원/ai 쪽 메시지를, 상담원은 고객/ai 쪽 메시지 기준으로 센다
        if (isCounselor(loginUser)) {
            long lastReadMessageId = room.getCounselorLastReadMessageId() == null ? 0L : room.getCounselorLastReadMessageId();
            return supportMessageRepository.countUnreadForCounselor(room.getId(), lastReadMessageId, loginUser.getId());
        }

        long lastReadMessageId = room.getCustomerLastReadMessageId() == null ? 0L : room.getCustomerLastReadMessageId();
        return supportMessageRepository.countUnreadForCustomer(room.getId(), lastReadMessageId, loginUser.getId());
    }
}
