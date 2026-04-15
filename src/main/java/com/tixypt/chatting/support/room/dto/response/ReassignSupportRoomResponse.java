package com.tixypt.chatting.support.room.dto.response;

// 문의방 재배정 결과 응답
public record ReassignSupportRoomResponse(
        Long roomId,
        Long counselorUserId,
        boolean reassigned
) {
}
