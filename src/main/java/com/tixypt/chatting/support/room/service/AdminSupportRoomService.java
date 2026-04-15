package com.tixypt.chatting.support.room.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.service.SupportSystemMessageService;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.dto.response.*;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
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
public class AdminSupportRoomService {

    private static final long UNREAD_COUNT_NOT_USED = 0L;

    private final SupportRoomRepository supportRoomRepository;
    private final MemberService memberService;
    private final SupportSystemMessageService supportSystemMessageService;

    @Value("${support.stale-room-minutes:30}")
    private long staleRoomMinutes;

    // 아직 상담사에게 배정되지 않은 OPEN 문의방만 대기열로 조회
    public SliceResponse<SupportRoomSummaryResponse> getQueueRooms(Long loginUserId, int page, int size) {
        SupportAccessPolicy.validateOperator(memberService.findById(loginUserId));
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);

        Slice<SupportRoomSummaryResponse> responseSlice = supportRoomRepository.findUnassignedOpenRooms(pageable)
                .map(room -> SupportRoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));

        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 종료 이력은 SUPER_ADMIN이면 전체, 일반 상담원이면 자신이 마지막으로 맡았던 방만 조회
    public SliceResponse<SupportRoomSummaryResponse> getClosedRooms(Long loginUserId, int page, int size) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);

        Slice<SupportRoomSummaryResponse> responseSlice = SupportAccessPolicy.isSuperAdmin(loginUser)
                ? supportRoomRepository.findByStatusOrderByIdDesc(SupportRoomStatus.CLOSED, pageable)
                .map(room -> SupportRoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED))
                : supportRoomRepository.findClosedRoomsForCounselor(loginUser.getId(), pageable)
                .map(room -> SupportRoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));

        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 오래 응답이 없는 배정 방 목록은 SUPER_ADMIN만 조회할 수 있음
    public SliceResponse<SupportRoomSummaryResponse> getStaleRooms(Long loginUserId, int page, int size) {
        SupportAccessPolicy.validateSuperAdmin(memberService.findById(loginUserId));
        Pageable pageable = PageableUtil.createSafePageRequest(page, size);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleRoomMinutes);

        Slice<SupportRoomSummaryResponse> responseSlice = supportRoomRepository.findStaleAssignedRooms(cutoff, pageable)
                .map(room -> SupportRoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));
        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 대기열 문의방을 현재 운영자에게 선점 배정
    // 이미 본인이 맡은 방이면 false 반환해서 멱등하게 처리하고 다른 운영자가 맡은 방이면 접근을 막음
    @Transactional
    public ClaimSupportRoomResponse claimRoom(Long loginUserId, Long roomId) {
        SupportAccessPolicy.validateOperator(memberService.findById(loginUserId));

        SupportRoom room = getRoomOrThrow(roomId);
        if (room.getCounselorUserId() != null && !room.getCounselorUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }

        if (room.getCounselorUserId() != null) {
            // 같은 상담원의 중복 claim은 현재 활동 시각만 갱신
            room.touchCounselorActivity(LocalDateTime.now());
            return new ClaimSupportRoomResponse(roomId, false);
        }

        boolean claimed = tryClaimRoom(loginUserId, roomId);
        if (claimed) {
            supportSystemMessageService.appendCounselorClaimedMessage(getRoomOrThrow(roomId));
        }
        return new ClaimSupportRoomResponse(roomId, claimed);
    }

    // release는 현재 담당을 해제해서 방을 다시 운영 대기열로 돌려 놓음
    // SUPER_ADMIN은 어떤 방이든 가능하고 일반 상담원은 자신이 맡은 방만 release할 수 있음
    @Transactional
    public ReleaseSupportRoomResponse releaseRoom(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);

        SupportRoom room = getRoomOrThrow(roomId);
        if (room.getCounselorUserId() == null) {
            return new ReleaseSupportRoomResponse(roomId, false);
        }

        if (!SupportAccessPolicy.isSuperAdmin(loginUser) && !room.getCounselorUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }

        room.releaseCounselor();
        supportSystemMessageService.appendCounselorReleasedMessage(room);
        return new ReleaseSupportRoomResponse(roomId, true);
    }

    // 재배정은 운영자가 현재 배정된 방을 다른 상담원한테 넘길 때 사용
    // 대상 상담원이 없거나 방이 아직 미배정이면 재배정으로 보지 않음
    @Transactional
    public ReassignSupportRoomResponse reassignRoom(Long loginUserId, Long roomId, Long targetCounselorUserId) {
        SupportAccessPolicy.validateSuperAdmin(memberService.findById(loginUserId));

        if (targetCounselorUserId == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }

        Member targetCounselor = memberService.findById(targetCounselorUserId);
        SupportAccessPolicy.validateAssignableCounselor(targetCounselor);
        SupportRoom room = getRoomOrThrow(roomId);

        if (room.getCounselorUserId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }

        if (targetCounselor.getId().equals(room.getCounselorUserId())) {
            // 같은 상담원으로의 재배정 요청은 배정 변경 없이 활동 시각만 갱신
            room.touchCounselorActivity(LocalDateTime.now());
            return new ReassignSupportRoomResponse(roomId, targetCounselorUserId, false);
        }

        room.forceAssignCounselor(targetCounselorUserId, LocalDateTime.now());
        supportSystemMessageService.appendCounselorReassignedMessage(room);
        return new ReassignSupportRoomResponse(roomId, targetCounselorUserId, true);
    }

    // solve는 최종 종료가 아니라 고객 추가 문의 가능성을 남겨 둔 해결 대기 상태 전환
    @Transactional
    public SolveSupportRoomResponse solveRoom(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportAccessPolicy.validateOperator(loginUser);

        SupportRoom room = getRoomOrThrow(roomId);
        validateRoomSolvable(room);
        validateSolveAccess(loginUser, room);

        boolean solved = room.solve();
        if (solved) {
            supportSystemMessageService.appendSolvedMessage(room);
        }
        return new SolveSupportRoomResponse(roomId, solved);
    }

    // 최종 종료는 운영 쪽에서만
    @Transactional
    public CloseSupportRoomResponse closeRoom(Long loginUserId, Long roomId) {
        SupportAccessPolicy.validateSuperAdmin(memberService.findById(loginUserId));

        SupportRoom room = getRoomOrThrow(roomId);
        boolean closed = room.close();
        if (closed) {
            supportSystemMessageService.appendClosedMessage(room);
        }
        return new CloseSupportRoomResponse(roomId, closed);
    }


    // 동시 claim 경쟁을 막기 위해서 조건부 update로 먼저 선점 시도
    // 선점에 실패하면 최신 상태를 다시 읽어서 중복 요청인지 다른 운영자의 선점인지 구분
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

    // 운영자 쪽에서 공통으로 사용하는 문의방 조회 메서드
    private SupportRoom getRoomOrThrow(Long roomId) {
        return supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));
    }

    private void validateSolveAccess(Member loginUser, SupportRoom room) {
        if (SupportAccessPolicy.isSuperAdmin(loginUser)) {
            return;
        }

        if (!loginUser.getId().equals(room.getCounselorUserId())) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    private void validateRoomSolvable(SupportRoom room) {
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ALREADY_CLOSED);
        }

        if (room.getCounselorUserId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }
    }
}
