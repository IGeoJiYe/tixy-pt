package com.tixypt.chatting.support.room.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.enums.MemberRole;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.room.dto.response.*;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.util.PageableUtil;
import lombok.RequiredArgsConstructor;
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

    // 아직 상담사에게 배정되지 않은 OPEN 문의방만 대기열로 조회
    public SliceResponse<SupportRoomSummaryResponse> getQueueRooms(Long loginUserId, int page, int size) {
        validateCounselor(memberService.findById(loginUserId));
        Pageable pageable = PageableUtil.createSafePageableDesc(page, size, "id");

        Slice<SupportRoomSummaryResponse> responseSlice = supportRoomRepository.findUnassignedOpenRooms(pageable)
                .map(room -> SupportRoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));

        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 현재 운영자가 마지막으로 처리한 CLOSED 문의방 이력만 조회
    public SliceResponse<SupportRoomSummaryResponse> getClosedRooms(Long loginUserId, int page, int size) {
        Member loginUser = memberService.findById(loginUserId);
        validateCounselor(loginUser);
        Pageable pageable = PageableUtil.createSafePageableDesc(page, size, "id");

        Slice<SupportRoomSummaryResponse> responseSlice = supportRoomRepository
                .findClosedRoomsForCounselor(loginUser.getId(), pageable)
                .map(room -> SupportRoomSummaryResponse.from(room, UNREAD_COUNT_NOT_USED));

        return SliceResponse.of(responseSlice.hasNext(), responseSlice.getContent());
    }

    // 대기열 문의방을 현재 운영자에게 선점 배정
    // 이미 본인이 맡은 방이면 false 반환해서 멱등하게 처리하고 다른 운영자가 맡은 방이면 접근을 막음
    @Transactional
    public ClaimSupportRoomResponse claimRoom(Long loginUserId, Long roomId) {
        validateCounselor(memberService.findById(loginUserId));

        SupportRoom room = getRoomOrThrow(roomId);
        if (room.getCounselorUserId() != null && !room.getCounselorUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }

        if (room.getCounselorUserId() != null) {
            room.touchCounselorActivity(LocalDateTime.now());
            return new ClaimSupportRoomResponse(roomId, false);
        }

        boolean claimed = tryClaimRoom(loginUserId, roomId);
        return new ClaimSupportRoomResponse(roomId, claimed);
    }

    // 현재 운영자가 맡은 문의방의 배정을 해제하고 다시 대기열로 되돌림
    // 이미 미배정 상태면 실제 변경이 없으니까 false를 반환
    @Transactional
    public ReleaseSupportRoomResponse releaseRoom(Long loginUserId, Long roomId) {
        validateCounselor(memberService.findById(loginUserId));

        SupportRoom room = getRoomOrThrow(roomId);
        if (room.getCounselorUserId() == null) {
            return new ReleaseSupportRoomResponse(roomId, false);
        }

        if (!room.getCounselorUserId().equals(loginUserId)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }

        room.releaseCounselor();
        return new ReleaseSupportRoomResponse(roomId, true);
    }

    // 현재 배정된 문의방을 다른 운영자에게 강제로 넘김
    // 미배정 방은 재배정 대상이 아니고 같은 운영자로 재배정하는 요청은 안 되게 처리
    @Transactional
    public ReassignSupportRoomResponse reassignRoom(Long loginUserId, Long roomId, Long targetCounselorUserId) {
        validateCounselor(memberService.findById(loginUserId));

        if (targetCounselorUserId == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }

        Member targetCounselor = memberService.findById(targetCounselorUserId);
        validateCounselor(targetCounselor);

        SupportRoom room = getRoomOrThrow(roomId);
        if (room.getCounselorUserId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }

        if (targetCounselor.getId().equals(room.getCounselorUserId())) {
            room.touchCounselorActivity(LocalDateTime.now());
            return new ReassignSupportRoomResponse(roomId, targetCounselorUserId, false);
        }

        room.forceAssignCounselor(targetCounselorUserId, LocalDateTime.now());
        return new ReassignSupportRoomResponse(roomId, targetCounselorUserId, true);
    }

    // 문의방을 CLOSED 상태로 전환하고 현재 배정 정보를 정리함
    // 이미 종료된 방이면 상태 변경 없이 false를 반환함
    @Transactional
    public CloseSupportRoomResponse closeRoom(Long loginUserId, Long roomId) {
        validateCounselor(memberService.findById(loginUserId));

        SupportRoom room = getRoomOrThrow(roomId);
        return new CloseSupportRoomResponse(roomId, room.close());
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

    // 운영자 전용 API 진입 전에 현재 사용자가 상담 권한을 가진 계정인지 확인
    private void validateCounselor(Member loginUser) {
        if (loginUser.getRole() != MemberRole.ADMIN) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }
}
