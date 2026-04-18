package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.config.AiProperties;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiReplyProviderRegistry {

    private final OpenAiReplyProvider openAiReplyProvider;
    private final OllamaReplyProvider ollamaReplyProvider;

    public AiReplyDraft generate(AiProperties.Provider provider, AiPromptContext promptContext) {
        return switch (provider) {
            case OPENAI -> openAiReplyProvider.generate(promptContext);
            case OLLAMA -> ollamaReplyProvider.generate(promptContext);
        };
    }

    public boolean isAvailable(AiProperties.Provider provider) {
        return switch (provider) {
            case OPENAI -> openAiReplyProvider.isAvailable();
            case OLLAMA -> ollamaReplyProvider.isAvailable();
        };
    }
}

