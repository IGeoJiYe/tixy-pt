package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

// ChatClient 기반 provider가 공통으로 따르는 실행 흐름을 모아 둔 것
// 1. 필수 설정 확인
// 2. ChatClient 준비했는지 확인
// 3. 모델 호출
// 4. 공통으로 후처리
// 5. 필요하면 provider 전용으로 각자 처리하는 거
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientReplyExecutor {

    private final AiReplyDraftFactory aiReplyDraftFactory;

    public AiReplyDraft execute(
            String providerName,
            AiPromptContext promptContext,
            Supplier<ChatClient> chatClientSupplier,
            String... requiredSettings
    ) {
        return execute(
                providerName,
                promptContext,
                chatClientSupplier,
                UnaryOperator.identity(),
                requiredSettings
        );
    }

    // 추가로 필요할 때만 후처리
    public AiReplyDraft execute(
            String providerName,
            AiPromptContext promptContext,
            Supplier<ChatClient> chatClientSupplier,
            UnaryOperator<AiReplyDraft> draftPostProcessor,
            String... requiredSettings
    ) {
        ChatClient chatClient = resolveClient(chatClientSupplier, requiredSettings);
        if (chatClient == null) {
            log.info("{} 설정이나 ChatClient가 준비되지 않아 fallback 응답으로 대체합니다.", providerName);
            return aiReplyDraftFactory.fallback();
        }

        try {
            String content = chatClient.prompt()
                    .user(promptContext.prompt())
                    .call()
                    .content();

            AiReplyDraft draft = aiReplyDraftFactory.toAnswer(content);
            return draftPostProcessor.apply(draft);
        } catch (RuntimeException exception) {
            log.warn("{} quick reply 생성에 실패해 fallback 응답으로 대체합니다.", providerName, exception);
            return aiReplyDraftFactory.fallback();
        }
    }

    public boolean isAvailable(Supplier<ChatClient> chatClientSupplier, String... requiredSettings) {
        return resolveClient(chatClientSupplier, requiredSettings) != null;
    }

    private ChatClient resolveClient(Supplier<ChatClient> chatClientSupplier, String... requiredSettings) {
        if (!hasRequiredSettings(requiredSettings)) {
            return null;
        }
        return chatClientSupplier.get();
    }

    private boolean hasRequiredSettings(String... requiredSettings) {
        for (String requiredSetting : requiredSettings) {
            if (!StringUtils.hasText(requiredSetting)) {
                return false;
            }
        }
        return true;
    }
}
