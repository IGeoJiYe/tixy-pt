package com.tixypt.chatting.support.room.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.message.service.SystemMessageService;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.dto.event.RoomQueueEvent;
import com.tixypt.chatting.support.room.dto.response.CreateRoomResponse;
import com.tixypt.chatting.support.room.dto.response.RequestCounselorResponse;
import com.tixypt.chatting.support.room.dto.response.RoomDetailResponse;
import com.tixypt.chatting.support.room.dto.response.RoomSummaryResponse;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.util.PageableUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private static final List<SupportRoomStatus> ACTIVE_ROOM_STATUSES = List.of(
            SupportRoomStatus.OPEN,
            SupportRoomStatus.SOLVED
    );

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SystemMessageService systemMessageService;
    private final MemberService memberService;
    private final SupportEventDispatcher supportEventDispatcher;
    private final EntityManager entityManager;

    // 고객이 현재 이어서 사용할 문의방을 확보
    // 이미 진행 중인 OPEN/SOLVED 방이 있으면 재사용하고 없을 때만 새 방 만듦
    @Transactional
    public CreateRoomResponse createRoom(Long loginUserId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateCustomerOnly(loginUser);
        lockCustomerRoomCreation(loginUser);

        return supportRoomRepository.findTopByCustomerUserIdAndStatusInOrderByIdDesc(loginUserId, ACTIVE_ROOM_STATUSES)
                .map(existingRoom -> new CreateRoomResponse(existingRoom.getId(), false))
                .orElseGet(() -> createNewOpenRoom(loginUserId));
    }

    // 같은 "/me" 목록이라도 고객, 상담원, SUPER_ADMIN이 보는 기준이 다르기 때문에 역할에 맞는 조회 쿼리 선택
    public SliceResponse<RoomSummaryResponse> getMyRooms(Long loginUserId, int page, int size) {
        Member loginUser = memberService.findById(loginUserId);
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);

        if (SupportAccessPolicy.isSuperAdmin(loginUser)) {
            return buildRoomSlice(
                    supportRoomRepository.findAllByOrderByIdDesc(pageable),
                    loginUser
            );
        }

        if (SupportAccessPolicy.isCounselor(loginUser)) {
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
    public RoomDetailResponse getRoomDetail(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = getRoomOrThrow(roomId);

        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        return RoomDetailResponse.from(room);
    }

    @Transactional
    public RequestCounselorResponse requestCounselor(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateCustomerOnly(loginUser);

        SupportRoom room = supportRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        SupportAccessPolicy.validateRoomWritable(room);

        boolean reopened = room.reopen();
        if (room.getCounselorUserId() != null) {
            return buildCounselorRequestResponse(room, false, reopened, false, true);
        }

        if (!room.requestCounselor(LocalDateTime.now())) {
            return buildCounselorRequestResponse(room, false, reopened, true, false);
        }

        systemMessageService.appendCounselorRequestedMessage(room);
        supportEventDispatcher.dispatchQueueEventAfterCommit(RoomQueueEvent.requested(room.getId()));
        return buildCounselorRequestResponse(room, true, reopened, false, false);
    }



    private RequestCounselorResponse buildCounselorRequestResponse(
            SupportRoom room,
            boolean requested,
            boolean reopened,
            boolean alreadyRequested,
            boolean alreadyAssigned
    ) {
        return new RequestCounselorResponse(
                room.getId(),
                room.getStatus(),
                room.getCounselorUserId(),
                room.getCustomerRequestedCounselorAt(),
                requested,
                reopened,
                alreadyRequested,
                alreadyAssigned
        );
    }

    // 방 생성 시점에는 담당 상담원이 정해지지 않았으니까 counselor는 null로 시작
    private CreateRoomResponse createNewOpenRoom(Long customerUserId) {
        SupportRoom room = supportRoomRepository.save(SupportRoom.open(customerUserId, null));
        systemMessageService.appendRoomCreatedMessage(room);
        return new CreateRoomResponse(room.getId(), true);
    }

    // 같은 고객의 문의방 생성 요청 고객 row 비관적 락
    private void lockCustomerRoomCreation(Member customer) {
        entityManager.lock(customer, LockModeType.PESSIMISTIC_WRITE);
    }

   // 문의방 Slice와 unread 집계를 묶어 최종 목록 응답으로 변환한다.
   private SliceResponse<RoomSummaryResponse> buildRoomSlice(Slice<SupportRoom> rooms, Member loginUser) {
       Map<Long, Long> unreadCountByRoomId = loadUnreadCountByRoomId(rooms.getContent(), loginUser);
       List<RoomSummaryResponse> items = rooms.getContent().stream()
               .map(room -> RoomSummaryResponse.from(
                       room,
                       unreadCountByRoomId.getOrDefault(room.getId(), 0L)
               ))
               .toList();
       return SliceResponse.of(rooms.hasNext(), items);
   }

   // 문의방 조회 -> 없으면 예외
    private SupportRoom getRoomOrThrow(Long roomId) {
        return supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));
    }

    // 현재 페이지에 보이는 문의방들의 unread count를 한 번에 집계
    private Map<Long, Long> loadUnreadCountByRoomId(List<SupportRoom> rooms, Member loginUser) {
        if (rooms.isEmpty() || SupportAccessPolicy.isSuperAdmin(loginUser)) {
            return Map.of();
        }

        List<Long> roomIds = rooms.stream()
                .map(SupportRoom::getId)
                .toList();

        List<SupportMessageRepository.RoomUnreadCountSummary> unreadCounts = SupportAccessPolicy.isCounselor(loginUser)
                ? supportMessageRepository.countUnreadForCounselorRooms(roomIds, loginUser.getId())
                : supportMessageRepository.countUnreadForCustomerRooms(roomIds, loginUser.getId());

        return unreadCounts.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SupportMessageRepository.RoomUnreadCountSummary::getRoomId,
                        SupportMessageRepository.RoomUnreadCountSummary::getUnreadCount
                ));
    }
}
