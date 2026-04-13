package com.tixypt.chatting.support.room.dto.response;

public record CloseSupportRoomResponse(
        Long roomId,
        boolean closed
) {
}
