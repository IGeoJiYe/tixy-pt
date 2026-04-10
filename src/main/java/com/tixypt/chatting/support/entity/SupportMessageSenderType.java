package com.tixypt.chatting.support.entity;

public enum SupportMessageSenderType {
    // 문의를 만든 고객이 보낸 메시지
    USER,

    // 상담원이 보낸 메시지
    COUNSELOR,

    // 선응답이나 안내형 자동 응답처럼 AI가 만든 메시지
    AI,

    // 시스템 알림처럼 사람도 AI도 아닌 내부 메시지
    SYSTEM
}
