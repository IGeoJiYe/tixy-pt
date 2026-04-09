package com.tixypt.core.util;


import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TestDataInit implements ApplicationRunner {

    private static final List<MemberSeed> MEMBER_SEEDS = List.of(
            new MemberSeed("Alice"),
            new MemberSeed("Bob")
    );

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (memberRepository.count() > 0) return;

        String password = passwordEncoder.encode("abc1234!");
        List<Member> savedMembers = new ArrayList<>();


        for (int i = 0; i < MEMBER_SEEDS.size(); i++) {
            MemberSeed seed = MEMBER_SEEDS.get(i);

            Member member = Member.builder()
                    .email(seed.name() + "@test.com")
                    .password(password)
                    .name(seed.name())
                    .build();

            Member savedMember = memberRepository.save(member);
            savedMembers.add(savedMember);
        }

        System.out.println("테스트용 계정 2개 세팅 완료!");
    }

    private record MemberSeed(
            String name
    ) {
    }
}