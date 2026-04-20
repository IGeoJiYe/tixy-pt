package com.tixypt.chatting.support.websocket;

import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.read.dto.event.ReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.UnreadCountSyncEvent;
import com.tixypt.chatting.support.room.dto.event.RoomQueueEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportRedisEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SupportStompEventBroadcaster supportStompEventBroadcaster;

    // Redis에서 받은 raw bytes를 SupportRedisEvent로 역직렬화한 뒤에 타입에 맞게 다시 로컬 브로드캐스터로 전달
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SupportRedisEvent event = objectMapper.readValue(message.getBody(), SupportRedisEvent.class);
            dispatch(event);
        } catch (JacksonException exception) {
            log.warn("지원 채팅 Redis 이벤트 역직렬화에 실패했습니다.", exception);
        }
    }

    // Redis 공통 이벤트를 실제 payload 타입으로 바꿔서 최종 전달은 로컬 broadcaster가 맡도록 함
    private void dispatch(SupportRedisEvent event) {
        switch (event.type()) {
            case MESSAGE -> supportStompEventBroadcaster.broadcastMessage(
                    objectMapper.convertValue(event.payload(), MessageEvent.class)
            );
            case READ_ROOM -> supportStompEventBroadcaster.broadcastReadRoom(
                    objectMapper.convertValue(event.payload(), ReadReceiptEvent.class)
            );
            case READ_USER -> supportStompEventBroadcaster.broadcastReadUser(
                    event.targetUserName(),
                    objectMapper.convertValue(event.payload(), UnreadCountSyncEvent.class)
            );
            case QUEUE -> supportStompEventBroadcaster.broadcastQueue(
                    objectMapper.convertValue(event.payload(), RoomQueueEvent.class)
            );
        }
    }
}
