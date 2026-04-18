package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.config.AiAdvisorConfig.SupportAiChatClientFactory;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// openai 전용 답변 생성 클래스
@Component
@RequiredArgsConstructor
public class OpenAiReplyProvider implements AiReplyProvider {

    private final SupportAiChatClientFactory supportAiChatClientFactory;
    private final ChatClientReplyExecutor chatClientReplyExecutor;

    @Value("${spring.ai.openai.chat.api-key:${spring.ai.openai.api-key:}}")
    private String openAiApiKey;

    @Override
    public AiReplyDraft generate(AiPromptContext promptContext) {
        return chatClientReplyExecutor.execute(
                "OpenAI",
                promptContext,
                supportAiChatClientFactory::openAiClient,
                openAiApiKey
        );
    }

    public boolean isAvailable() {
        return chatClientReplyExecutor.isAvailable(
                supportAiChatClientFactory::openAiClient,
                openAiApiKey
        );
    }
}
