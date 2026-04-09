package com.tixypt.api.member.entity;

import com.tixypt.api.member.enums.MemberRole;
import com.tixypt.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// @AllArgsConstructor 지양: 필드 순서 변경 시 위험
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100) // OAuth2 로그인 시 비밀번호가 없을 수 있으므로 nullable = true (기본값)
    private String password;

    @Column(unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING) // Enum은 반드시 STRING으로! (ORDINAL 금지: 순서 바뀌면 DB 꼬임)
    @Column(nullable = false)
    private MemberRole role;

    @Builder
    private Member(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = MemberRole.USER; // 기본값 강제 세팅: 가입 시엔 무조건 USER
    }
}