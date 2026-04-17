package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.config.SupportAiAdvisorConfig.SupportAiChatClientFactory;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Ollama provider 전용 선응답 실행기
// 실제 ChatClient 호출과 후처리는 공통 실행기에 맡기고
// 이 클래스는 1. 올라마가 쓸 준비가 되었는가 2. 어떤 클라이언트를 써야 하는가만 표현
@Component
@RequiredArgsConstructor
public class OllamaReplyProvider implements AiReplyProvider {

    private final SupportAiChatClientFactory supportAiChatClientFactory;
    private final ChatClientReplyExecutor chatClientReplyExecutor;

    @Value("${spring.ai.ollama.base-url")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollana.chat.options.model:}")
    private String ollamaModel;

    // 올라마 전용 준비 조건
    @Override
    public AiReplyDraft generate(AiPromptContext promptContext) {
        return chatClientReplyExecutor.execute(
                "Ollama",
                promptContext,
                supportAiChatClientFactory::ollamaClient,
                ollamaBaseUrl,
                ollamaModel
        );
    }

    // 올라마 설정값이랑 ChatClient 준비를 바탕으로 사용 가능한 상태인지 판단
    public boolean isAvailable() {
        return chatClientReplyExecutor.isAvailable(
                supportAiChatClientFactory::ollamaClient,
                ollamaBaseUrl,
                ollamaModel
        );
    }
}
