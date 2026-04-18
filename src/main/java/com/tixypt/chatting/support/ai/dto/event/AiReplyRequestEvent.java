package com.tixypt.chatting.support.ai.dto.event;

// 고객 메시지 저장이 끝난 뒤에 자동 ai 응답을 시도하는 내부 이벤트
public record AiReplyRequestEvent(
        Long roomId,
        Long triggerMessageId
) {
}
