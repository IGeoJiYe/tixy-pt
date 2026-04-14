package com.tixypt.chatting.support.websocket;

import com.tixypt.chatting.support.message.dto.request.SupportMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalSupportEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    // 저장이 끝난 메시지를 문의방 구독 채널로 바로 내보내서 동일한 이벤트를 받게 함
    public void broadcastMessage(SupportMessageEvent event) {
        messagingTemplate.convertAndSend(
                SupportStompDestination.SUBSCRIBE_PREFIX + "/rooms/" + event.roomId(),
                event
        );
    }

}
