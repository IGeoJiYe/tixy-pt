package com.tixypt.core.config;

import com.tixypt.chatting.support.websocket.SupportStompDestination;
import com.tixypt.chatting.support.websocket.auth.SupportAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SupportAuthChannelInterceptor supportAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 방 구독 채널이랑 사용자 개인 unread 동기화 채널을 simple broker가 직접 처리하게 함께 연다
        registry.enableSimpleBroker(
                SupportStompDestination.SUBSCRIBE_PREFIX,
                SupportStompDestination.BROKER_USER_QUEUE_PREFIX
        );

        registry.setApplicationDestinationPrefixes(SupportStompDestination.PUBLISH_PREFIX);
        registry.setUserDestinationPrefix(SupportStompDestination.USER_DESTINATION_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 브라우저 디버그 콘솔은 순수 WebSocket으로 바로 붙기 때문에 기본 endpoint를 함께 연다
        registry.addEndpoint(SupportStompDestination.ENDPOINT)
                .setAllowedOriginPatterns("*");

        // SockJS fallback 경로는 기존 테스트와 호환을 위해 계속 유지
        registry.addEndpoint(SupportStompDestination.ENDPOINT)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // STOMP CONNECT 단계 jwt 인증은 inbound channel interceptor가 담당
        registration.interceptors(supportAuthChannelInterceptor);
    }
}