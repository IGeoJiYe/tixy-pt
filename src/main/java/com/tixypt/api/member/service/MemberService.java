package com.tixy.api.member.service;

import com.tixy.api.auth.dto.SignUpRequest;
import com.tixy.api.member.entity.Member;
import com.tixy.api.member.repository.MemberRepository;
import com.tixy.core.exception.MemberErrorCode;
import com.tixy.core.exception.MemberException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원 생성 (auth 도메인에서 회원가입 시 위임받아 실행)
     * 중복 검증 + 비밀번호 암호화 + 저장 처리
     */
    @Transactional
    public Member createMember(SignUpRequest signUpRequest) {
        String emailOrPhone = signUpRequest.emailOrPhone();
        String email = emailOrPhone.contains("@") ? emailOrPhone : null;
        String phone = emailOrPhone.contains("@") ? null : emailOrPhone;

        // 이메일/전화번호 중복체크
        if (email != null && memberRepository.existsByEmail(email)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (phone != null && memberRepository.existsByPhone(phone)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_PHONE);
        }

        // 사용자 이름 중복체크
        if (memberRepository.existsByUsername(signUpRequest.username())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_USERNAME);
        }

        Member member = Member.builder()
                .username(signUpRequest.username())
                .password(passwordEncoder.encode(signUpRequest.password()))
                .email(email)
                .phone(phone)
                .name(signUpRequest.name())
                .build();

        return memberRepository.save(member);
    }

    /**
     * 로그인 ID(이메일/전화번호/유저네임) 기반 회원 조회 (auth 도메인에서 로그인 시 위임)
     */
    public Member findByLoginId(String loginId) {
        if (loginId.contains("@")) {
            return memberRepository.findByEmail(loginId)
                    .orElseThrow(() -> new MemberException(MemberErrorCode.INVALID_CREDENTIALS));
        } else if (loginId.matches("^[0-9\\-]+$")) {
            return memberRepository.findByPhone(loginId)
                    .orElseThrow(() -> new MemberException(MemberErrorCode.INVALID_CREDENTIALS));
        } else {
            return memberRepository.findByUsername(loginId)
                    .orElseThrow(() -> new MemberException(MemberErrorCode.INVALID_CREDENTIALS));
        }
    }

    /**
     * ID 기반 회원 조회
     */
    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * username 기반 회원 조회
     *
     * 프론트 라우트가 /:username 형태일 때
     * 프로필 페이지 진입용 API에서 사용한다.
     */
    public Member findByUsername(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * DB I/O 없이 연관관계 설정용 Proxy 객체만 필요할 때 (post 도메인에서 위임)
     * ex) Post 생성 시 writer FK 설정
     */
    public Member getReferenceById(Long memberId) {
        return memberRepository.getReferenceById(memberId);
    }
}