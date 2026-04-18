package com.tixypt.chatting.support.ai.service;

import com.tixypt.chatting.support.ai.config.RagDocumentLoader;
import com.tixypt.chatting.support.ai.config.AiProperties;
import com.tixypt.chatting.support.ai.dto.AiStatusResponse;
import com.tixypt.chatting.support.ai.provider.AiReplyProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 현재 ai 실행 환경을 읽기 쉬운 상태로 조림
// 나중에 없앨 수도?
@Service
@RequiredArgsConstructor
public class AiStatusService {

    private final AiProperties aiProperties;
    private final RagDocumentLoader ragDocumentLoader;
    private final AiReplyProviderRegistry aiReplyProviderRegistry;
    private final ObjectProvider<Advisor> advisorProvider;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @Value("${spring.ai.openai.chat.options.model:gpt-4.1-mini}")
    private String openAiModel;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String ollamaModel;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public AiStatusResponse getCurrentStatus() {
        boolean ragEnabled = aiProperties.isRagEnabled();
        boolean ragAdvisorReady = ragEnabled && advisorProvider.getIfAvailable() != null;
        boolean ragVectorStoreReady = ragEnabled && vectorStoreProvider.getIfAvailable() != null;

        return new AiStatusResponse(
                aiProperties.getMode().name(),
                aiProperties.getProvider().name(),
                ragEnabled,
                ragAdvisorReady,
                ragVectorStoreReady,
                ragDocumentLoader.count(),
                aiProperties.getRagTopK(),
                aiProperties.getRagSimilarityThreshold(),
                aiProperties.getRecentMessageContextLimit(),
                aiProperties.getMaxResponseCharacters(),
                aiReplyProviderRegistry.isAvailable(AiProperties.Provider.OPENAI),
                aiReplyProviderRegistry.isAvailable(AiProperties.Provider.OLLAMA),
                openAiModel,
                ollamaModel,
                ollamaBaseUrl
        );
    }
}
