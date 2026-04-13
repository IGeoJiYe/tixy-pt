package com.tixypt.chatting.support.room.dto.response;

public record ReassignSupportRoomResponse(
        Long roomId,
        Long counselorUserId,
        boolean reassigned
) {
}
