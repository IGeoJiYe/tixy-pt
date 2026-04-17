package com.tixypt.chatting.support.ai.model;

public record AiPromptContext(
        String latestCustomerMessage,           // 현재 문의 주체를 빠르게 판단할 때 씀
        String prompt                           // ChatClient에 user 메시지로 그대로 전달할 본문
) {
}
