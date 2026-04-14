package com.tixypt.core.util;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.enums.MemberRole;
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

    private static final List<MemberSeed> MEMBER_SEEDS = new ArrayList<>();

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (memberRepository.count() > 0) return;

        String password = passwordEncoder.encode("abc1234!");
        List<Member> savedMembers = new ArrayList<>();

        MEMBER_SEEDS.add(new MemberSeed("hyeonji", "010-1234-1234", MemberRole.SUPER_ADMIN));
        MEMBER_SEEDS.add(new MemberSeed("jiwon", "010-2345-2345", MemberRole.ADMIN));
        MEMBER_SEEDS.add(new MemberSeed("jaemin", "010-3456-3456", MemberRole.ADMIN));
        MEMBER_SEEDS.add(new MemberSeed("kichan", "010-4567-4567", MemberRole.ADMIN));

        for (int i = 1; i <= 20; i++) {
            MEMBER_SEEDS.add(new MemberSeed("admin" + i, String.format("010-1000-%04d", i), MemberRole.ADMIN));
        }
        for (int i = 1; i <= 80; i++) {
            MEMBER_SEEDS.add(new MemberSeed("user" + i, String.format("010-%04d-1000", i), MemberRole.USER));
        }
        for (int i = 0; i < MEMBER_SEEDS.size(); i++) {
            MemberSeed seed = MEMBER_SEEDS.get(i);

            Member member = Member.builder()
                    .email(seed.name() + "@test.com")
                    .password(password)
                    .name(seed.name())
                    .phone(seed.phone())
                    .role(seed.role())
                    .build();

            Member savedMember = memberRepository.save(member);
            savedMembers.add(savedMember);
        }

        System.out.println("테스트용 계정 " + MEMBER_SEEDS.size() + "개 세팅 완료!");
    }

    private record MemberSeed(
            String name,
            String phone,
            MemberRole role
    ) {
    }
}