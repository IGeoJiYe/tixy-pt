package com.tixypt.chatting.support.room.dto.response;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;

import java.time.LocalDateTime;

// 방 상세 조회 응답
public record RoomDetailResponse(
        Long roomId,
        Long customerUserId,
        Long counselorUserId,
        LocalDateTime customerRequestedCounselorAt,
        SupportRoomStatus status,
        Long lastMessageId,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RoomDetailResponse from(SupportRoom room) {
        return new RoomDetailResponse(
                room.getId(),
                room.getCustomerUserId(),
                room.getCounselorUserId(),
                room.getCustomerRequestedCounselorAt(),
                room.getStatus(),
                room.getLastMessageId(),
                room.getLastMessageAt(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
