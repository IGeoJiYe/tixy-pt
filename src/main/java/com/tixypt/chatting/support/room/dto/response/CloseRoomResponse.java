package com.tixypt.chatting.support.room.dto.response;

public record CloseRoomResponse(
        Long roomId,
        boolean closed
) {
}
