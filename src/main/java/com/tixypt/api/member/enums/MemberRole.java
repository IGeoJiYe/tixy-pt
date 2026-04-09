package com.tixy.api.member.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberRole {

    // Spring Security에서는 권한 이름이 보통 "ROLE_"로 시작해야 인식이 쉽습니다.
    USER("ROLE_USER", "일반 사용자"),
    ADMIN("ROLE_ADMIN", "관리자");

    private final String key;
    private final String title;
}