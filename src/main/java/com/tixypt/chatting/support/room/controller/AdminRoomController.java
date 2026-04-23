package com.tixypt.chatting.support.room.controller;

import com.tixypt.chatting.support.room.dto.request.RoomPageRequest;
import com.tixypt.chatting.support.room.dto.response.*;
import com.tixypt.chatting.support.room.service.AdminRoomService;
import com.tixypt.core.dto.ApiResponse;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.security.annotation.LoginUser;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tixypt/api/admin/support/v1")
public class AdminRoomController {

    private final AdminRoomService adminRoomService;

    // 운영자 화면에서 아직 누구도 맡지 않은 OPEN 문의방 대기열 조회
    @GetMapping("/queue")
    public ApiResponse<SliceResponse<RoomSummaryResponse>> getQueueRooms(
            @LoginUser LoginUserInfoDto loginUser,
            @Valid @ModelAttribute RoomPageRequest query
    ) {
        return ApiResponse.success(
                adminRoomService.getQueueRooms(loginUser, query.getPage(), query.getSize())
        );
    }

    // 운영자가 자신이 마지막으로 처리한 종료 문의방 이력 조회
    @GetMapping("/rooms/closed")
    public ApiResponse<SliceResponse<RoomSummaryResponse>> getClosedRooms(
            @LoginUser LoginUserInfoDto loginUser,
            @Valid @ModelAttribute RoomPageRequest query
    ) {
        return ApiResponse.success(
                adminRoomService.getClosedRooms(loginUser, query.getPage(), query.getSize())
        );
    }

    // SUPER_ADMIN 전용 조회: 장기 미응답 방 조회
    @GetMapping("/rooms/stale")
    public ApiResponse<SliceResponse<RoomSummaryResponse>> getStaleRooms(
            @LoginUser LoginUserInfoDto loginUser,
            @Valid @ModelAttribute RoomPageRequest query
    ) {
        return ApiResponse.success(
                adminRoomService.getStaleRooms(loginUser, query.getPage(), query.getSize())
        );
    }

    // 대기열의 문의방을 배정
    @PostMapping("/rooms/{roomId}/claim")
    public ApiResponse<ClaimRoomResponse> claimRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(adminRoomService.claimRoom(loginUser, roomId));
    }

    // 현재 운영자가 맡고 있는 문의방을 다시 대기열 상태로 되돌림. 이미 미배정 상태인 경우에는 추가 변경 없이 처리
    @PostMapping("/rooms/{roomId}/release")
    public ApiResponse<ReleaseRoomResponse> releaseRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(adminRoomService.releaseRoom(loginUser, roomId));
    }

    @PostMapping("/rooms/{roomId}/solve")
    public ApiResponse<SolveRoomResponse> solveRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(adminRoomService.solveRoom(loginUser, roomId));
    }

    // 상담사가 문의방을 종료 상태로 바꿈
    @PostMapping("/rooms/{roomId}/close")
    public ApiResponse<CloseRoomResponse> closeRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(adminRoomService.closeRoom(loginUser, roomId));
    }
}
