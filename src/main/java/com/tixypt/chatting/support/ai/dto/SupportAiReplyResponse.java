package com.tixypt.chatting.support.ai.dto;

import com.tixypt.chatting.support.message.dto.event.SupportMessageEvent;

// 실제로 저장된 메시지 이벤트랑 fallback인지 아닌지를 함께 내려서 진짜 모델 응답인지 기본 대체 문구인지 구분할 수 있게 함
public record SupportAiReplyResponse(
        SupportMessageEvent message,    // 실제 저장된 ai 메시지 이벤트
        boolean fallback                // true면 모델 응답 대신에 fallback 문구가 사용된 것
) {
}
