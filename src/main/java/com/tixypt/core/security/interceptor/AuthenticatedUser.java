package com.tixypt.core.security.interceptor;

import java.security.Principal;

import com.tixypt.api.member.entity.Member;
import lombok.Getter;

@Getter
public class AuthenticatedUser implements Principal {

    private final Member member;
    private final String name;

    public AuthenticatedUser(Member member) {
        this.member = member;
        this.name = member.getName();
    }

    // Principal에서 User 꺼내기
    public static Member fromPrincipal(Principal principal) {
        return ((AuthenticatedUser) principal).getMember();
    }
}