package com.tixypt.api.member.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.repository.MemberRepository;
import com.tixypt.core.exception.MemberErrorCode;
import com.tixypt.core.exception.MemberException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    /**
     * 로그인 ID(이메일/전화번호/유저네임) 기반 회원 조회 (auth 도메인에서 로그인 시 위임)
     */
    public Member findByLoginId(String loginId) {
        return memberRepository.findByEmail(loginId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.INVALID_CREDENTIALS));
    }

    /**
     * ID 기반 회원 조회
     */
    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
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