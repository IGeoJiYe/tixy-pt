package com.tixypt.chatting.support.message.dto.request;

import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportMessageSenderType;
import com.tixypt.chatting.support.entity.SupportMessageType;

import java.time.LocalDateTime;

public record SupportMessageEvent(
        Long roomId,
        Long messageId,
        Long senderUserId,
        SupportMessageSenderType senderType,
        SupportMessageType messageType,
        String content,
        LocalDateTime createdAt
) {
    // 엔티티를 STOMP 전송 전용 이벤트 형태로 바꿈
    public static SupportMessageEvent from(SupportMessage message) {
        return new SupportMessageEvent(
                message.getRoom().getId(),
                message.getId(),
                message.getSenderUserId(),
                message.getSenderType(),
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
