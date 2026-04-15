package com.tixypt.chatting.support.websocket;

import com.tixypt.chatting.support.message.dto.event.SupportMessageEvent;
import com.tixypt.chatting.support.read.dto.event.SupportReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.SupportUnreadSyncEvent;
import com.tixypt.chatting.support.room.dto.event.SupportRoomQueueEvent;

public record SupportRedisEvent(
        SupportRedisEventType type,
        Long roomId,
        String targetUserName,
        Object payload
) {

    // 방 메시지 이벤트
    public static SupportRedisEvent message(SupportMessageEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.MESSAGE, payload.roomId(), null, payload);
    }

    // 방 읽음
    public static SupportRedisEvent readRoom(SupportReadReceiptEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.READ_ROOM, payload.roomId(), null, payload);
    }

    // 개인 unread sync
    public static SupportRedisEvent readUser(String targetUserName, SupportUnreadSyncEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.READ_USER, payload.roomId(), targetUserName, payload);
    }

    // queue
    public static SupportRedisEvent queue(SupportRoomQueueEvent payload) {
        return new SupportRedisEvent(SupportRedisEventType.QUEUE, payload.roomId(), null, payload);
    }
}
