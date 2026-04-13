package com.tixypt.chatting.support.room.controller;

import com.tixypt.chatting.support.room.dto.request.ReassignSupportRoomRequest;
import com.tixypt.chatting.support.room.dto.request.SupportRoomSliceQueryRequest;
import com.tixypt.chatting.support.room.dto.response.*;
import com.tixypt.chatting.support.room.service.AdminSupportRoomService;
import com.tixypt.core.dto.ApiResponse;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.security.annotation.LoginUser;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/support/v1")
public class AdminSupportRoomController {

    private final AdminSupportRoomService adminSupportRoomService;

    @GetMapping("/queue")
    // 운영자 화면에서 아직 누구도 맡지 않은 OPEN 문의방 대기열 조회
    public ApiResponse<SliceResponse<SupportRoomSummaryResponse>> getQueueRooms(
            @LoginUser LoginUserInfoDto loginUser,
            @Valid @ModelAttribute SupportRoomSliceQueryRequest query
    ) {
        return ApiResponse.success(
                adminSupportRoomService.getQueueRooms(loginUser.id(), query.getPage(), query.getSize())
        );
    }

    @GetMapping("/rooms/closed")
    // 운영자가 자신이 마지막으로 처리한 종료 문의방 이력 조회
    public ApiResponse<SliceResponse<SupportRoomSummaryResponse>> getClosedRooms(
            @LoginUser LoginUserInfoDto loginUser,
            @Valid @ModelAttribute SupportRoomSliceQueryRequest query
    ) {
        return ApiResponse.success(
                adminSupportRoomService.getClosedRooms(loginUser.id(), query.getPage(), query.getSize())
        );
    }

    @PostMapping("/rooms/{roomId}/claim")
    // 대기열의 문의방을 현재 운영자에게 배정
    public ApiResponse<ClaimSupportRoomResponse> claimRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(adminSupportRoomService.claimRoom(loginUser.id(), roomId));
    }

    @PostMapping("/rooms/{roomId}/release")
    // 현재 운영자가 맡고 있는 문의방을 다시 대기열 상태로 되돌림 이미 미배정 상태인 경우에는 추가 변경 없이 처리
    public ApiResponse<ReleaseSupportRoomResponse> releaseRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(adminSupportRoomService.releaseRoom(loginUser.id(), roomId));
    }

    @PostMapping("/rooms/{roomId}/reassign")
    // 현재 배정된 문의방을 다른 운영자에게 강제로 재배정
    public ApiResponse<ReassignSupportRoomResponse> reassignRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId,
            @RequestBody ReassignSupportRoomRequest request
    ) {
        return ApiResponse.success(
                adminSupportRoomService.reassignRoom(
                        loginUser.id(),
                        roomId,
                        request == null ? null : request.targetCounselorUserId()
                )
        );
    }

    @PostMapping("/rooms/{roomId}/close")
    // 운영자가 문의방을 종료 상태로 전환
    public ApiResponse<CloseSupportRoomResponse> closeRoom(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(adminSupportRoomService.closeRoom(loginUser.id(), roomId));
    }
}
