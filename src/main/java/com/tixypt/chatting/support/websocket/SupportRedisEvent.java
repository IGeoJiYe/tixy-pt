package com.tixypt.chatting.support.websocket;

import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.read.dto.event.ReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.UnreadCountSyncEvent;
import com.tixypt.chatting.support.room.dto.event.RoomQueueEvent;

public record SupportRedisEvent(
        SupportRedisEventType type,
        Long roomId,
        String targetUserName,
        Object payload
) {

    // 방 메시지 이벤트
    public static SupportRedisEvent message(MessageEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.MESSAGE, payload.roomId(), null, payload);
    }

    // 방 읽음
    public static SupportRedisEvent readRoom(ReadReceiptEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.READ_ROOM, payload.roomId(), null, payload);
    }

    // 개인 unread sync
    public static SupportRedisEvent readUser(String targetUserName, UnreadCountSyncEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.READ_USER, payload.roomId(), targetUserName, payload);
    }

    // queue
    public static SupportRedisEvent queue(RoomQueueEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.QUEUE, payload.roomId(), null, payload);
    }
}
