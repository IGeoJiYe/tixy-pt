package com.tixypt.chatting.support.room.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.service.SystemMessageService;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.dto.event.RoomQueueEvent;
import com.tixypt.chatting.support.room.dto.response.*;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRoomService {

    private static final long UNREAD_COUNT_NOT_USED = 0L;

    private final SupportRoomRepository supportRoomRepository;
    private final SystemMessageService systemMessageService;
    private final MemberService memberService;
    private final SupportEventDispatcher supportEventDispatcher;

    @Value("${support.stale-room-minutes:30}")
    private long staleRoomMinutes;

    // 아직 상담사에게 배정되지 않은 OPEN 문의방만 대기열로 조회
    public SliceResponse<RoomSummaryResponse> getQueueRooms(Long loginUserId, int page, int size) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);

        Slice<RoomSummaryResponse> responseSlice = supportRoomRepository.findUnassignedOpenRooms(pageable)
                .map(room -> RoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));
        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 종료 이력은 SUPER_ADMIN이면 전체, 일반 상담원이면 자신이 마지막으로 맡았던 방만 조회
    public SliceResponse<RoomSummaryResponse> getClosedRooms(Long loginUserId, int page, int size) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);

        Slice<RoomSummaryResponse> responseSlice = SupportAccessPolicy.isSuperAdmin(loginUser)
                ? supportRoomRepository.findByStatusOrderByIdDesc(SupportRoomStatus.CLOSED, pageable)
                .map(room -> RoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED))
                : supportRoomRepository.findClosedRoomsForCounselor(loginUser.getId(), pageable)
                .map(room -> RoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));

        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 오래 응답이 없는 배정 방 목록은 SUPER_ADMIN만 조회할 수 있음
    public SliceResponse<RoomSummaryResponse> getStaleRooms(Long loginUserId, int page, int size) {
        SupportAccessPolicy.validateSuperAdmin(memberService.findById(loginUserId));
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleRoomMinutes);

        Slice<RoomSummaryResponse> responseSlice = supportRoomRepository.findStaleAssignedRooms(cutoff, pageable)
                .map(room -> RoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));
        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 대기열 문의방을 현재 운영자에게 선점 배정
    // 이미 본인이 맡은 방이면 false 반환해서 멱등하게 처리하고 다른 운영자가 맡은 방이면 접근을 막음
    @Transactional
    public ClaimRoomResponse claimRoom(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);

        SupportRoom room = getRoomOrThrow(roomId);
        if (room.getCounselorUserId() != null && !room.getCounselorUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }

        if (room.getCounselorUserId() != null) {
            // 같은 상담원의 중복 claim은 현재 활동 시각만 갱신
            room.touchCounselorActivity(LocalDateTime.now());
            return new ClaimRoomResponse(roomId, false);
        }

        boolean claimed = tryClaimRoom(loginUserId, roomId);
        if (claimed) {
            systemMessageService.appendCounselorClaimedMessage(getRoomOrThrow(roomId));
            supportEventDispatcher.dispatchQueueEventAfterCommit(RoomQueueEvent.claimed(roomId, loginUser.getId()));
        }
        return new ClaimRoomResponse(roomId, claimed);
    }

    // release는 현재 담당을 해제해서 방을 다시 운영 대기열로 돌려 놓음
    // SUPER_ADMIN은 어떤 방이든 가능하고 일반 상담원은 자신이 맡은 방만 release할 수 있음
    @Transactional
    public ReleaseRoomResponse releaseRoom(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);

        SupportRoom room = getRoomForUpdateOrThrow(roomId);
        if (room.getCounselorUserId() == null) {
            return new ReleaseRoomResponse(roomId, false);
        }

        if (!SupportAccessPolicy.isSuperAdmin(loginUser) && !room.getCounselorUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }

        room.releaseCounselor();
        systemMessageService.appendCounselorReleasedMessage(room);
        supportEventDispatcher.dispatchQueueEventAfterCommit(RoomQueueEvent.released(roomId));
        return new ReleaseRoomResponse(roomId, true);
    }

    // 재배정은 운영자가 현재 배정된 방을 다른 상담원한테 넘길 때 사용
    // 대상 상담원이 없거나 방이 아직 미배정이면 재배정으로 보지 않음
    @Transactional
    public ReassignRoomResponse reassignRoom(Long loginUserId, Long roomId, Long targetCounselorUserId) {
        SupportAccessPolicy.validateSuperAdmin(memberService.findById(loginUserId));

        if (targetCounselorUserId == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }

        Member targetCounselor = memberService.findById(targetCounselorUserId);
        SupportAccessPolicy.validateAssignableCounselor(targetCounselor);
        SupportRoom room = getRoomForUpdateOrThrow(roomId);

        if (room.getCounselorUserId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }

        if (targetCounselor.getId().equals(room.getCounselorUserId())) {
            // 같은 상담원으로의 재배정 요청은 배정 변경 없이 활동 시각만 갱신한다.
            room.touchCounselorActivity(LocalDateTime.now());
            return new ReassignRoomResponse(roomId, targetCounselorUserId, false);
        }

        room.forceAssignCounselor(targetCounselorUserId, LocalDateTime.now());
        systemMessageService.appendCounselorReassignedMessage(room);
        return new ReassignRoomResponse(roomId, targetCounselorUserId, true);
    }

    // solve는 최종 종료가 아니라 고객 추가 문의 가능성을 남겨 둔 해결 대기 상태 전환
    @Transactional
    public SolveRoomResponse solveRoom(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);

        SupportRoom room = getRoomForUpdateOrThrow(roomId);
        validateRoomSolvable(room);
        validateSolveAccess(loginUser, room);

        boolean solved = room.solve(LocalDateTime.now());
        if (solved) {
            systemMessageService.appendSolvedMessage(room);
            supportEventDispatcher.dispatchQueueEventAfterCommit(RoomQueueEvent.solved(roomId));
        }
        return new SolveRoomResponse(roomId, solved);
    }

    // 최종 종료는 운영 쪽에서만
    @Transactional
    public CloseRoomResponse closeRoom(Long loginUserId, Long roomId) {
        SupportAccessPolicy.validateSuperAdmin(memberService.findById(loginUserId));

        SupportRoom room = getRoomForUpdateOrThrow(roomId);
        boolean closed = room.close();
        if (closed) {
            systemMessageService.appendClosedMessage(room);
            supportEventDispatcher.dispatchQueueEventAfterCommit(RoomQueueEvent.closed(roomId));
        }
        return new CloseRoomResponse(roomId, closed);
    }



    // 문의방을 일반 조회로 읽고 없으면 공통 예외
    private SupportRoom getRoomOrThrow(Long roomId) {
        return supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));
    }

    // 문의방 조회 수정에서 사용 row-lock
    private SupportRoom getRoomForUpdateOrThrow(Long roomId) {
        return supportRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));
    }

    // 조건부 update로 선점 경쟁 처리
    // update 성공 했을 때 진짜 선접을 하고 실패 후에 재조회했을 때 이미 내가 선점한 상태면 중복 요청
    // 둘 다 아니면 다른 상담원이 먼저 가져간 거로 판단
    private boolean tryClaimRoom(Long loginUserId, Long roomId) {
        int updated = supportRoomRepository.claimCounselorIfUnassigned(roomId, loginUserId, LocalDateTime.now());
        if (updated == 1) {
            return true;
        }

        SupportRoom latestRoom = getRoomOrThrow(roomId);
        if (loginUserId.equals(latestRoom.getCounselorUserId())) {
            return false;
        }

        throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
    }

    // solve를 할 수 있는지 역할이랑 담당자 기준으로 검증
    private void validateSolveAccess(Member loginUser, SupportRoom room) {
        if (SupportAccessPolicy.isSuperAdmin(loginUser)) {
            return;
        }

        if (!loginUser.getId().equals(room.getCounselorUserId())) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // solve 가능한 문의방 상태인지 확인
    private void validateRoomSolvable(SupportRoom room) {
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ALREADY_CLOSED);
        }

        if (room.getCounselorUserId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }
    }
}
