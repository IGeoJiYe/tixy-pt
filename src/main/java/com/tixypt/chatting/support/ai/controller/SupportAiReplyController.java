package com.tixypt.chatting.support.ai.controller;

import com.tixypt.chatting.support.ai.dto.SupportAiReplyResponse;
import com.tixypt.chatting.support.ai.service.SupportAiReplyService;
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
public class SupportAiReplyController {

    private final SupportAiReplyService supportAiReplyService;

    @PostMapping("/{roomId}/ai-replies")
    public ApiResponse<SupportAiReplyResponse> createAiReply(
            @LoginUser LoginUserInfoDto loginUser,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(supportAiReplyService.createAiReply(loginUser.id(), roomId));
    }
}
