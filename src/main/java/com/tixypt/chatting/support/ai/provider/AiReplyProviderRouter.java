package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.config.AiProperties;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// support.ai.mode /support.ai.provider 설정되어 있는 값을 보고 지금 어떤 답변 전략을 사용할지 결정하는 라우터
// POLICY - 규칙 기반 응답 사용
// PROVIDER - 선택한 AI provider 하나만 사용
// HYBRID - provider를 먼저 시도하고 응답이 비거나 fallback이면 정책으로 감
@Primary
@Component
@RequiredArgsConstructor
public class AiReplyProviderRouter implements AiReplyProvider {

    private final AiProperties aiProperties;
    private final PolicyBasedReplyProvider policyBasedReplyProvider;
    private final AiReplyProviderRegistry aiReplyProviderRegistry;

    @Override
    public AiReplyDraft generate(AiPromptContext promptContext) {
        return switch (aiProperties.getMode()) {
            case POLICY -> policyBasedReplyProvider.generate(promptContext);
            case PROVIDER -> generateWithSelectedProvider(promptContext);
            case HYBRID -> generateHybrid(promptContext);
        };
    }

    // 하이브리드 모드는 ai 응답을 먼저 시도하긴 하지만 실패하면 정책 응답으로 감
    private AiReplyDraft generateHybrid(AiPromptContext promptContext) {
        AiReplyDraft providerAnswer = generateWithSelectedProvider(promptContext);
        if (isUsable(providerAnswer)) {
            return providerAnswer;
        }
        return policyBasedReplyProvider.generate(promptContext);
    }

    private AiReplyDraft generateWithSelectedProvider(AiPromptContext promptContext) {
        return aiReplyProviderRegistry.generate(aiProperties.getProvider(), promptContext);
    }

    // provider 응답이 실제 서비스에 써도 되는 최소 조건만 판단 내용이 비어 있거나 fallback 표시 있으면 빽
    private boolean isUsable(AiReplyDraft answer) {
        return answer != null && StringUtils.hasText(answer.content()) && !answer.fallback();
    }
}
