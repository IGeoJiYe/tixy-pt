package com.tixypt.chatting.support.room.dto.event;

// 상담원 대기열 구독자에게 보내는 방 상태 변경 이벤트
public record SupportRoomQueueEvent(
        Long roomId,
        SupportRoomQueueEventType eventType,
        Long counselorUserId
) {
    // 상담원이 방을 가져가서 queue에서 빠질 때
    public static SupportRoomQueueEvent claimed(Long roomId, Long counselorUserId) {
        return new SupportRoomQueueEvent(roomId, SupportRoomQueueEventType.CLAIMED, counselorUserId);
    }

    // 상담원이 수동으로 release 해서 다시 queue로 돌아갈 때 신호
    public static SupportRoomQueueEvent released(Long roomId) {
        return new SupportRoomQueueEvent(roomId, SupportRoomQueueEventType.RELEASED, null);
    }

    // 방을 종료되어서 queue랑 운영 화면에서 정리되어야 할 때 신호
    public static SupportRoomQueueEvent closed(Long roomId) {
        return new SupportRoomQueueEvent(roomId, SupportRoomQueueEventType.CLOSED, null);
    }
}
