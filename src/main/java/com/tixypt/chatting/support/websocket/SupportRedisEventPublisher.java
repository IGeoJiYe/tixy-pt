package com.tixypt.chatting.support.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportRedisEventPublisher {

    // support 채팅 이벤트를 모아서 발행하는 단일 topic
    public static final String TOPIC = "support-events";

    private final RedisTemplate<String, Object> redisTemplate;

    // 실제 발행은 RedisTemplate의 convertAndSend 한 번으로 끝냄
    public void publish(SupportRedisEvent event) {
        redisTemplate.convertAndSend(TOPIC, event);
    }
}
