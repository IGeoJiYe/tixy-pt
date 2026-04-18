package com.tixypt.chatting.support.ai.controller;

import com.tixypt.chatting.support.ai.dto.AiReplyResponse;
import com.tixypt.chatting.support.ai.service.AiReplyService;
import com.tixypt.core.dto.ApiResponse;
import com.tixypt.core.security.annotation.LoginUser;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 문의방 AI 선응답 생성용 컨트롤러
// AI 응답 생성
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support/v1/rooms")
public class AiReplyController {

    private final AiReplyService aiReplyService;

    @PostMapping("/{roomId}/ai-replies")
    public ApiResponse<AiReplyResponse> createAiReply(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(aiReplyService.createAiReply(loginUser.id(), roomId));
    }
}
