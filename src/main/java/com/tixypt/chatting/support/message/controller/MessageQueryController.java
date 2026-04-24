package com.tixypt.chatting.support.message.controller;

import com.tixypt.chatting.support.message.dto.request.MessageCursorRequest;
import com.tixypt.chatting.support.message.dto.response.MessageCursorResponse;
import com.tixypt.chatting.support.message.service.MessageService;
import com.tixypt.core.dto.ApiResponse;
import com.tixypt.core.security.annotation.LoginUser;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tixypt/api/support")
public class MessageQueryController {

    private final MessageService messageService;

    // 문의방 메시지 이력을 커서 기반으로 조회
    @GetMapping("/v1/rooms/{roomId}/messages")
    public ApiResponse<MessageCursorResponse> getRoomMessages(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId,
            @Valid @ModelAttribute MessageCursorRequest query
    ) {
        return ApiResponse.success(
                messageService.getMessages(
                        loginUser,
                        roomId,
                        query.getBeforeMessageId(),
                        query.getSize()
                )
        );
    }

}
