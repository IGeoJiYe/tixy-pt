package com.tixypt.chatting.support.room.controller;

import com.tixypt.chatting.support.room.dto.response.CreateSupportRoomResponse;
import com.tixypt.chatting.support.room.dto.response.SupportRoomDetailResponse;
import com.tixypt.chatting.support.room.dto.request.SupportRoomSliceQueryRequest;
import com.tixypt.chatting.support.room.dto.response.SupportRoomSummaryResponse;
import com.tixypt.chatting.support.room.service.SupportRoomService;
import com.tixypt.core.dto.ApiResponse;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.security.annotation.LoginUser;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support/v1/rooms")
public class SupportRoomController {

    private final SupportRoomService supportRoomService;

    @PostMapping
    public ApiResponse<CreateSupportRoomResponse> createRoom(
            @LoginUser LoginUserInfoDto loginUser
    ) {
        // 고객 입장에서는 무조건 새 방 생성보다 지금 대화할 문의방 확보에 가까우니까 항상 공통 성공 응답
        // 기존 OPEN 방 재사용 여부는 payload의 created 값으로 구분
        return ApiResponse.success(supportRoomService.createRoom(loginUser.id()));
    }

    @GetMapping("/me")
    public ApiResponse<SliceResponse<SupportRoomSummaryResponse>> getMyRooms(
            @LoginUser LoginUserInfoDto loginUser,
            @Valid @ModelAttribute SupportRoomSliceQueryRequest query
    ) {
        return ApiResponse.success(supportRoomService.getMyRooms(loginUser.id(), query.getPage(), query.getSize()));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<SupportRoomDetailResponse> getRoomDetail(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(supportRoomService.getRoomDetail(loginUser.id(), roomId));
    }
}
