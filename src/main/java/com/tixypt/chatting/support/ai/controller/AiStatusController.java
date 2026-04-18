package com.tixypt.chatting.support.ai.controller;

import com.tixypt.chatting.support.ai.dto.AiStatusResponse;
import com.tixypt.chatting.support.ai.service.AiStatusService;
import com.tixypt.core.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 현재 ai 실행 상태를 조회함
// 이것도 나중에 없앨 수도...... 일단 조회용으로 둠
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/support/v1/ai")
public class AiStatusController {

    private final AiStatusService aiStatusService;

    @GetMapping("/config")
    public ApiResponse<AiStatusResponse> getConfig() {
        return ApiResponse.success(aiStatusService.getCurrentStatus());
    }
}
