package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.config.AiProperties;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.prompt.AiReplyPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

// Ai 응답 후처리 규칙
// 1. 빈 응답 fallback
// 2. 줄바꿈과 공백 처리
// 3. 최대 길이 제한
// 4. 프롬프트 마커 노출 차단
// 나머지 모델별로 품질을 보정하는 건 각자 처리
@Component
@RequiredArgsConstructor
public class AiReplyDraftFactory {

    private static final String[] PROMPT_MARKERS = {
            "[문의방 ID]",
            "[현재 고객 질문]",
            "[최근 대화 흐름",
            "[응답 작성 가이드]"
    };

    private final AiProperties aiProperties;

    public AiReplyDraft toAnswer(String content) {
        String normalizedContent = normalize(content);
        if (!StringUtils.hasText(normalizedContent)) {
            return fallback();
        }

        String candidate = truncate(normalizedContent);
        if (containsPromptMarker(candidate)) {
            return fallback();
        }

        return AiReplyDraft.normal(candidate);
    }

    public AiReplyDraft fallback() {
        return AiReplyDraft.fallback(AiReplyPolicy.FALLBACK_REPLY);
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
        int maxCharacters = aiProperties.getMaxResponseCharacters();
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

    // 프롬프트가 응답에 그대로 섞인 건 아닌지 확인한다
    private boolean containsPromptMarker(String content) {
        for (String promptMarker : PROMPT_MARKERS) {
            if (content.contains(promptMarker)) {
                return true;
            }
        }
        return false;
    }
}
