package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

// ChatClient 기반 provider들이 공통으로 수행하는 실행 흐름
// 1. 필수 설정 확인
// 2. ChatClient 준비 여부 확인
// 3. 모델 호출
// 4. 예외 때 fallback

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientReplyExecutor {

    private final AiReplyResultMapper aiReplyResultMapper;

    public AiReplyDraft execute(
            String providerName,
            AiPromptContext promptContext,
            Supplier<ChatClient> chatClientSupplier,
            String... requiredSettings
    ) {
        ChatClient chatClient = resolveClient(chatClientSupplier, requiredSettings);
        if (chatClient == null) {
            log.info("{} 설정이나 ChatClient가 준비되지 않아 fallback 응답으로 대체합니다.", providerName);
            return aiReplyResultMapper.fallback();
        }

        try {
            String content = chatClient.prompt()
                    .user(promptContext.prompt())
                    .call()
                    .content();

            return aiReplyResultMapper.toAnswer(content);
        } catch (RuntimeException exception) {
            log.warn("{} quick reply 생성에 실패해 fallback 응답으로 대체합니다.", providerName, exception);
            return aiReplyResultMapper.fallback();
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
