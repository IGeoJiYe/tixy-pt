package com.tixypt.chatting.support.read.dto.event;

import java.time.LocalDateTime;

// 현재 사용자 개인채널로 보내는 unread 동기화 이벤트
// 목록 배지처럼 개인 화면만 갱신할 때 사용
public record SupportUnreadSyncEvent(
        Long roomId,
        Long lastReadMessageId,
        long unreadCount,
        LocalDateTime readAt
) {
}
