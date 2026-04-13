package com.tixypt.chatting.support.room.dto;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;

import java.time.LocalDateTime;

// 방 상세 조회 응답
public record SupportRoomDetailResponse(
        Long roomId,
        Long customerUserId,
        Long counselorUserId,
        SupportRoomStatus status,
        Long lastMessageId,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SupportRoomDetailResponse from(SupportRoom room) {
        return new SupportRoomDetailResponse(
                room.getId(),
                room.getCustomerUserId(),
                room.getCounselorUserId(),
                room.getStatus(),
                room.getLastMessageId(),
                room.getLastMessageAt(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
