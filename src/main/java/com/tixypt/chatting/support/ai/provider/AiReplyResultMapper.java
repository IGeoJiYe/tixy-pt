package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.config.SupportAiProperties;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.prompt.SupportAiReplyPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiReplyResultMapper {

    private final SupportAiProperties supportAiProperties;

    // 1. 빈 응답이면 fallback으로 전환
    // 2. 줄바꿈과 과한 공백을 정리해서 채팅 메시지처럼 다듬어
    // 3. 너무 긴 응답은 운영 정책 기준의 길이 안으로 잘라내
    public AiReplyDraft toAnswer(String content) {
        String normalizedContent = normalize(content);
        if (!StringUtils.hasText(normalizedContent)) {
            return fallback();
        }

        if (normalizedContent.length() <= supportAiProperties.getMaxResponseCharacters()) {
            return AiReplyDraft.normal(normalizedContent);
        }

        return AiReplyDraft.normal(truncate(normalizedContent));
    }

    public AiReplyDraft fallback() {
        return AiReplyDraft.fallback(SupportAiReplyPolicy.FALLBACK_REPLY);
    }

    private String normalize(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }

        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "))
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String truncate(String content) {
        int maxCharacters = supportAiProperties.getMaxResponseCharacters();
        if (maxCharacters <= 0 || content.length() <= maxCharacters) {
            return content;
        }

        int wordBoundary = content.lastIndexOf(' ', maxCharacters);
        if (wordBoundary >= maxCharacters / 2) {
            int previousBoundary = content.lastIndexOf(' ', wordBoundary - 1);
            if (previousBoundary >= maxCharacters / 3 && maxCharacters - wordBoundary <= 4) {
                return content.substring(0, previousBoundary).trim();
            }
            return content.substring(0, wordBoundary).trim();
        }
        return content.substring(0, maxCharacters).trim();
    }
}
