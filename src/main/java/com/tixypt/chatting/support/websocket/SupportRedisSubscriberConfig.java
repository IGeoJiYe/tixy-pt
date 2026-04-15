package com.tixypt.chatting.support.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class SupportRedisSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer supportRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SupportRedisEventSubscriber subscriber
    ) {
        // 이 컨테이너가 레디스 토픽을 계속 구독하고 있다가 다른 인스턴스가 발행한 문의 채팅 이벤트를 받아 줌
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // topic 이름은 publisher랑 subscriber가 정확히 같아야 됨
        container.addMessageListener(subscriber, new PatternTopic(SupportRedisEventPublisher.TOPIC));
        return container;
    }
}
