package com.tixypt.chatting.support.room.dto.response;

// 문의방 해결 처리 결과 응답
public record SolveRoomResponse(
        Long roomId,
        boolean solved
) {
}
