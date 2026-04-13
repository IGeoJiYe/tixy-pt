package com.tixypt.chatting.support.message.dto.response;

import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportMessageSenderType;
import com.tixypt.chatting.support.entity.SupportMessageType;

import java.time.LocalDateTime;

// roomId는 경로에 이미 포함되어 있으니까 메시지 자체 정보만 담음
public record SupportMessageResponse(
        Long messageId,
        Long senderUserId,
        SupportMessageSenderType senderType,
        SupportMessageType messageType,
        String content,
        LocalDateTime createdAt
) {
    public static SupportMessageResponse from(SupportMessage message) {
        return new SupportMessageResponse(
                message.getId(),
                message.getSenderUserId(),
                message.getSenderType(),
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
