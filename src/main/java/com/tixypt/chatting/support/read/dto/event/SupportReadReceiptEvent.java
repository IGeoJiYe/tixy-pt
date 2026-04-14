package com.tixypt.chatting.support.read.dto.event;

import java.time.LocalDateTime;

// 방 전체 구독 채널로 보내는 읽음 이벤트
// 같은 문의방 화면에서 상대 읽음 위치를 갱신할 때 사용
public record SupportReadReceiptEvent(
        Long roomId,
        Long readerUserId,
        String readerRole,
        Long lastReadMessageId,
        LocalDateTime readAt
) {
}
