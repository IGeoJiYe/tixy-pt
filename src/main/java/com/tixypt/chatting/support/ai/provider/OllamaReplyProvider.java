package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.config.AiAdvisorConfig.SupportAiChatClientFactory;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ollama 전용
@Component
@RequiredArgsConstructor
public class OllamaReplyProvider implements AiReplyProvider {

    private static final Pattern FOREIGN_WORD_PATTERN = Pattern.compile("\\b[A-Za-z]{3,}\\b");
    private static final int MAX_ALLOWED_FOREIGN_WORD_COUNT = 1;

    private final SupportAiChatClientFactory supportAiChatClientFactory;
    private final ChatClientReplyExecutor chatClientReplyExecutor;
    private final AiReplyDraftFactory aiReplyDraftFactory;

    @Value("${spring.ai.ollama.base-url:}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:}")
    private String ollamaModel;

    @Override
    public AiReplyDraft generate(AiPromptContext promptContext) {
        return chatClientReplyExecutor.execute(
                "Ollama",
                promptContext,
                supportAiChatClientFactory::ollamaClient,
                this::applyOllamaQualityGuard,
                ollamaBaseUrl,
                ollamaModel
        );
    }

    public boolean isAvailable() {
        return chatClientReplyExecutor.isAvailable(
                supportAiChatClientFactory::ollamaClient,
                ollamaBaseUrl,
                ollamaModel
        );
    }

    // Ollama는 다국어가 섞이거나 깨진 문자로 답하는 경우가 있어서 공통 후처리 이후에 한 번 더 품질 검사를 태움
    private AiReplyDraft applyOllamaQualityGuard(AiReplyDraft draft) {
        if (draft == null || draft.fallback()) {
            return draft;
        }

        String content = draft.content();
        if (containsTooManyForeignWords(content) || containsExtendedLatinCharacters(content)) {
            return aiReplyDraftFactory.fallback();
        }
        return draft;
    }

    private boolean containsTooManyForeignWords(String content) {
        Matcher matcher = FOREIGN_WORD_PATTERN.matcher(content);
        int foreignWordCount = 0;
        while (matcher.find()) {
            foreignWordCount++;
            if (foreignWordCount > MAX_ALLOWED_FOREIGN_WORD_COUNT) {
                return true;
            }
        }
        return false;
    }

    private boolean containsExtendedLatinCharacters(String content) {
        return content.codePoints()
                .anyMatch(codePoint ->
                        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN
                                && codePoint > 127
                );
    }
}
