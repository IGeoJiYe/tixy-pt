package com.tixypt.chatting.support.ai.controller;

import com.tixypt.chatting.support.ai.dto.SupportAiConfigResponse;
import com.tixypt.chatting.support.ai.service.SupportAiStatusService;
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
public class SupportAiConfigController {

    private final SupportAiStatusService supportAiStatusService;

    @GetMapping("/config")
    public ApiResponse<SupportAiConfigResponse> getConfig() {
        return ApiResponse.success(supportAiStatusService.getCurrentStatus());
    }
}
