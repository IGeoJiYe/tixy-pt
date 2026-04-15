package com.tixypt.chatting.support.websocket.auth;

import lombok.Getter;
import org.springframework.security.authentication.BadCredentialsException;

import java.security.Principal;

@Getter
public class SupportStompPrincipal implements Principal {

    private final Long userId;
    private final String role;
    private final String name;

    public SupportStompPrincipal(Long userId, String role) {
        this.userId = userId;
        this.role = role;
        this.name = String.valueOf(userId);
    }

    // STOMP handler에서는 Principal 타입이 바로 비즈니스 입력이 되니까 기대한 인증 주체가 아니면 바로 인증 예외로 끊음
    public static SupportStompPrincipal from(Principal principal) {
        if (!(principal instanceof SupportStompPrincipal supportStompPrincipal)) {
            throw new BadCredentialsException("STOMP 인증 정보가 올바르지 않습니다.");
        }
        return supportStompPrincipal;
    }
}
