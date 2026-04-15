package com.tixypt.chatting.support.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tixypt.chatting.support.message.dto.event.SupportMessageEvent;
import com.tixypt.chatting.support.read.dto.event.SupportReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.SupportUnreadSyncEvent;
import com.tixypt.chatting.support.room.dto.event.SupportRoomQueueEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportRedisEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final LocalSupportEventBroadcaster localSupportEventBroadcaster;

    // Redis에서 받은 raw bytes를 SupportRedisEvent로 역직렬화한 뒤에 타입에 맞게 다시 로컬 브로드캐스터로 전달
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SupportRedisEvent event = objectMapper.readValue(message.getBody(), SupportRedisEvent.class);
            dispatch(event);
        } catch (Exception exception) {
            log.error("문의 채팅 Redis 이벤트 역직렬화에 실패했습니다", exception);
        }
    }

    // Redis 공통 이벤트를 실제 payload 타입으로 바꿔서 최종 전달은 로컬 broadcaster가 맡도록 함
    private void dispatch(SupportRedisEvent event) {
        switch (event.type()) {
            case MESSAGE -> localSupportEventBroadcaster.broadcastMessage(
                    objectMapper.convertValue(event.payload(), SupportMessageEvent.class)
            );
            case READ_ROOM -> localSupportEventBroadcaster.broadcastReadRoom(
                    objectMapper.convertValue(event.payload(), SupportReadReceiptEvent.class)
            );
            case READ_USER -> localSupportEventBroadcaster.broadcastReadUser(
                    event.targetUserName(),
                    objectMapper.convertValue(event.payload(), SupportUnreadSyncEvent.class)
            );
            case QUEUE -> localSupportEventBroadcaster.broadcastQueue(
                    objectMapper.convertValue(event.payload(), SupportRoomQueueEvent.class)
            );
        }
    }
}
