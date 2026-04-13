package com.tixypt.chatting.support.room.dto;

// 고객이 지금 진입해야 할 문의방 정보 응답
// created 값으로 새 방 생성인지 기존 open 방 재사용인지 함께 구분
public record CreateSupportRoomResponse(
        Long roomId,
        boolean created
) {
}
