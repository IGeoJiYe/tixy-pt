package com.tixypt.chatting.support.room.dto.response;

public record ClaimRoomResponse(
        Long roomId,
        boolean claimed
) {
}
