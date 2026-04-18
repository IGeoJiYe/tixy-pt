package com.tixypt.chatting.support.room.dto.response;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;

import java.time.LocalDateTime;

public record RoomSummaryResponse(
        // 문의방 id
        Long roomId,

        // OPEN, CLOSED 같은 방 상태
        SupportRoomStatus status,

        // 마지막 메시지 id
        Long lastMessageId,

        // 마지막 메시지 시각
        LocalDateTime lastMessageAt,

        // 방이 처음 생성된 시각
        LocalDateTime createdAt,

        LocalDateTime customerRequestedCounselorAt,

        // 현재 로그인 사용자가 아직 읽지 않은 메시지 수
        long unreadCount
) {
    public static RoomSummaryResponse from(SupportRoom room, long unreadCount) {
        return new RoomSummaryResponse(
                room.getId(),
                room.getStatus(),
                room.getLastMessageId(),
                room.getLastMessageAt(),
                room.getCreatedAt(),
                room.getCustomerRequestedCounselorAt(),
                unreadCount
        );
    }
}
