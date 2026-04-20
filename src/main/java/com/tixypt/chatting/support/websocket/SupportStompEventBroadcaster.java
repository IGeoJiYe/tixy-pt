package com.tixypt.chatting.support.websocket;

import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.read.dto.event.ReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.UnreadCountSyncEvent;
import com.tixypt.chatting.support.room.dto.event.RoomQueueEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportStompEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    // 저장이 끝난 메시지를 문의방 구독 채널로 바로 내보내서 동일한 이벤트를 받게 함
    public void broadcastMessage(MessageEvent event) {
        messagingTemplate.convertAndSend(
                SupportStompDestination.SUBSCRIBE_PREFIX + "/rooms/" + event.roomId(),
                event
        );
    }

    // 읽음 이벤트는 방 전체 구독 채널로 보내서 같은 방 화면에서 상대 읽음 상태를 함께 갱신할 수 있게 함
    public void broadcastReadRoom(ReadReceiptEvent event) {
        messagingTemplate.convertAndSend(
                SupportStompDestination.SUBSCRIBE_PREFIX + "/rooms/" + event.roomId() + "/read",
                event
        );
    }

    // unread sync는 방 전체가 아니라 현재 사용자 개인 채널로 보내서 목록 badge 같은 개인 상태만 갱신
    public void broadcastReadUser(String userName, UnreadCountSyncEvent event) {
        messagingTemplate.convertAndSendToUser(
                userName,
                SupportStompDestination.BROKER_USER_QUEUE_PREFIX + "/read",
                event
        );
    }

    // 대기열 이벤트는 queue 구독자 전체가 보는 destination으로 보냄
    public void broadcastQueue(RoomQueueEvent event) {
        messagingTemplate.convertAndSend(
                SupportStompDestination.SUBSCRIBE_PREFIX + "/queue",
                event
        );
    }
}
