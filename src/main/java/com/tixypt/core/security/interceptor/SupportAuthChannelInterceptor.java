package com.tixypt.core.security.interceptor;


import com.tixypt.core.constant.AuthConstants;
import com.tixypt.core.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportAuthChannelInterceptor implements ChannelInterceptor {

    private static final String SESSION_USER_ID = "userId";
    private static final String SESSION_ROLE = "role";

    private final JwtTokenProvider jwtTokenProvider;

    // CONNECT 프레임에서만 인증을 처리하고 이후 SUBSCRIBE/SEND는 이미 저장된 세션 사용자 정보를 사용
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = resolveToken(accessor);
        authenticate(accessor, token);
        return message;
    }

    // STOMP는 http 필터 체인을 그대로 타지 않으니까 native header의 Authorization 값을 직접 읽어서 Bearer 토큰을 확인
    private String resolveToken(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader(AuthConstants.AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            log.warn("STOMP CONNECT 인증 실패: Authorization 헤더가 없거나 Bearer 형식이 아닙니다.");
            throw new BadCredentialsException("STOMP CONNECT에 Bearer 토큰이 필요합니다.");
        }

        return authorizationHeader.substring(AuthConstants.BEARER_PREFIX.length());
    }

    // 토큰 검증이 끝나면 userId와 role을 STOMP 세션에 보관해서 이후 메시지 처리에서 토큰을 다시 파싱하지 않도록 함
    private void authenticate(StompHeaderAccessor accessor, String token) {
        try {
            jwtTokenProvider.validateToken(token);
        } catch (RuntimeException exception) {
            log.warn("STOMP CONNECT 인증 실패: 토큰 검증에 실패했습니다. reason={}", exception.getClass().getSimpleName());
            throw exception;
        }

        Long userId = jwtTokenProvider.getMemberId(token);
        String role = jwtTokenProvider.getRole(token);

        if (userId == null || !StringUtils.hasText(role)) {
            log.warn("STOMP CONNECT 인증 실패: 토큰에 userId 또는 role 정보가 없습니다.");
            throw new BadCredentialsException("STOMP CONNECT 초큰에 필수 인증 정보가 없습니다.");
        }

        SupportStompPrincipal principal = new SupportStompPrincipal(userId, role);
        accessor.setUser(principal);

        if (accessor.getSessionAttributes() == null) {
            accessor.setSessionAttributes(new HashMap<>());
        }

        accessor.getSessionAttributes().put(SESSION_USER_ID, userId);
        accessor.getSessionAttributes().put(SESSION_ROLE, role);

        log.info("STOMP CONNECT 인증 성공: userId={}, role={}", userId, role);
    }
}
