package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.prompt.SupportAiReplyPolicy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

// 외부 ai provider 없어도 동작하는 규칙 기반 답변 생성 클래스
// 키워드 기반으로 환불, 배송, 일정, 결제 같은 기본 카테고리를 나눠서 CS 문구 만듦
@Component
public class PolicyBasedReplyProvider implements AiReplyProvider {

    @Override
    public AiReplyDraft generate(AiPromptContext promptContext) {
        String normalizedMessage = normalize(promptContext.latestCustomerMessage());

        if (!StringUtils.hasText(normalizedMessage)) {
            return AiReplyDraft.normal(SupportAiReplyPolicy.EMPTY_CUSTOMER_MESSAGE_REPLY);
        }

        if (containsAny(normalizedMessage, SupportAiReplyPolicy.REFUND_KEYWORDS)) {
            return AiReplyDraft.normal(SupportAiReplyPolicy.REFUND_REPLY);
        }

        if (containsAny(normalizedMessage, SupportAiReplyPolicy.DELIVERY_KEYWORDS)) {
            return AiReplyDraft.normal(SupportAiReplyPolicy.DELIVERY_REPLY);
        }

        if (containsAny(normalizedMessage, SupportAiReplyPolicy.SCHEDULE_KEYWORDS)) {
            return AiReplyDraft.normal(SupportAiReplyPolicy.SCHEDULE_REPLY);
        }

        if (containsAny(normalizedMessage, SupportAiReplyPolicy.PAYMENT_KEYWORDS)) {
            return AiReplyDraft.normal(SupportAiReplyPolicy.PAYMENT_REPLY);
        }

        return AiReplyDraft.normal(SupportAiReplyPolicy.DEFAULT_REPLY);
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
