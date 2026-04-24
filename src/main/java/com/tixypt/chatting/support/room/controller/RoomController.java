package com.tixypt.chatting.support.room.controller;

import com.tixypt.chatting.support.room.dto.response.CreateRoomResponse;
import com.tixypt.chatting.support.room.dto.response.RequestCounselorResponse;
import com.tixypt.chatting.support.room.dto.response.RoomDetailResponse;
import com.tixypt.chatting.support.room.dto.request.RoomPageRequest;
import com.tixypt.chatting.support.room.dto.response.RoomSummaryResponse;
import com.tixypt.chatting.support.room.service.RoomService;
import com.tixypt.core.dto.ApiResponse;
import com.tixypt.core.dto.SliceResponse;
import com.tixypt.core.security.annotation.LoginUser;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tixypt/api/support")
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/v1/rooms")
    public ApiResponse<CreateRoomResponse> createRoom(
            @LoginUser LoginUserInfoDto loginUser
    ) {
        // 고객 입장에서는 무조건 새 방 생성보다 지금 대화할 문의방 확보에 가까우니까 항상 공통 성공 응답
        // 기존 OPEN 방 재사용 여부는 payload의 created 값으로 구분
        return ApiResponse.success(roomService.createRoom(loginUser));
    }

    @PostMapping("/v1/rooms/{roomId}/counselor-request")
    public ApiResponse<RequestCounselorResponse> requestCounselor(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(roomService.requestCounselor(loginUser, roomId));
    }

    @GetMapping("/v1/rooms/me")
    public ApiResponse<SliceResponse<RoomSummaryResponse>> getMyRooms(
            @LoginUser LoginUserInfoDto loginUser,
            @Valid @ModelAttribute RoomPageRequest query
    ) {
        return ApiResponse.success(roomService.getMyRooms(loginUser, query.getPage(), query.getSize()));
    }

    @GetMapping("/v1/rooms/{roomId}")
    public ApiResponse<RoomDetailResponse> getRoomDetail(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(roomService.getRoomDetail(loginUser, roomId));
    }
}
