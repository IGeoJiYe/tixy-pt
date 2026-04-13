package com.tixypt.chatting.support.message.controller;

import com.tixypt.chatting.support.message.dto.request.SupportMessageQueryRequest;
import com.tixypt.chatting.support.message.dto.response.SupportMessageSliceResponse;
import com.tixypt.chatting.support.message.service.SupportMessageService;
import com.tixypt.core.dto.ApiResponse;
import com.tixypt.core.security.annotation.LoginUser;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support/v1/rooms")
public class SupportMessageQueryController {

    private final SupportMessageService supportMessageService;

    // 문의방 메시지 이력을 커서 기반으로 조회
    @GetMapping("/{roomId}/messages")
    public ApiResponse<SupportMessageSliceResponse> getRoomMessages(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId,
            @Valid @ModelAttribute SupportMessageQueryRequest query
            ) {
        return ApiResponse.success(
                supportMessageService.getMessages(
                        loginUser.id(),
                        roomId,
                        query.getBeforeMessageId(),
                        query.getSize()
                )
        );
    }

}
