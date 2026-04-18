package com.tixypt.chatting.support.room.dto.event;

// 상담원 대기열 구독자에게 보내는 방 상태 변경 이벤트
public record RoomQueueEvent(
        Long roomId,
        RoomQueueEventType eventType,
        Long counselorUserId
) {
    // 상담원이 방을 가져가서 queue에서 빠질 때
    public static RoomQueueEvent claimed(Long roomId, Long counselorUserId) {
        return new RoomQueueEvent(roomId, RoomQueueEventType.CLAIMED, counselorUserId);
    }

    // 상담원이 수동으로 release 해서 다시 queue로 돌아갈 때 신호
    public static RoomQueueEvent released(Long roomId) {
        return new RoomQueueEvent(roomId, RoomQueueEventType.RELEASED, null);
    }

    public static RoomQueueEvent requested(Long roomId) {
        return new RoomQueueEvent(roomId, RoomQueueEventType.REQUESTED, null);
    }

    public static RoomQueueEvent solved(Long roomId) {
        return new RoomQueueEvent(roomId, RoomQueueEventType.SOLVED, null);
    }

    // 방을 종료되어서 queue랑 운영 화면에서 정리되어야 할 때 신호
    public static RoomQueueEvent closed(Long roomId) {
        return new RoomQueueEvent(roomId, RoomQueueEventType.CLOSED, null);
    }
}
