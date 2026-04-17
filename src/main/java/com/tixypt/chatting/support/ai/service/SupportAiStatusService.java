package com.tixypt.chatting.support.ai.service;

import com.tixypt.chatting.support.ai.config.SupportAiDocumentLoader;
import com.tixypt.chatting.support.ai.config.SupportAiProperties;
import com.tixypt.chatting.support.ai.dto.SupportAiConfigResponse;
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
public class SupportAiStatusService {

    private final SupportAiProperties supportAiProperties;
    private final SupportAiDocumentLoader supportAiDocumentLoader;
    private final AiReplyProviderRegistry aiReplyProviderRegistry;
    private final ObjectProvider<Advisor> advisorProvider;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @Value("${spring.ai.openai.chat.options.model:gpt-4.1-mini}")
    private String openAiModel;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String ollamaModel;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public SupportAiConfigResponse getCurrentStatus() {
        boolean ragEnabled = supportAiProperties.isRagEnabled();
        boolean ragAdvisorReady = ragEnabled && advisorProvider.getIfAvailable() != null;
        boolean ragVectorStoreReady = ragEnabled && vectorStoreProvider.getIfAvailable() != null;

        return new SupportAiConfigResponse(
                supportAiProperties.getMode().name(),
                supportAiProperties.getProvider().name(),
                ragEnabled,
                ragAdvisorReady,
                ragVectorStoreReady,
                supportAiDocumentLoader.count(),
                supportAiProperties.getRagTopK(),
                supportAiProperties.getRagSimilarityThreshold(),
                supportAiProperties.getRecentMessageContextLimit(),
                supportAiProperties.getMaxResponseCharacters(),
                aiReplyProviderRegistry.isAvailable(SupportAiProperties.Provider.OPENAI),
                aiReplyProviderRegistry.isAvailable(SupportAiProperties.Provider.OLLAMA),
                openAiModel,
                ollamaModel,
                ollamaBaseUrl
        );
    }
}
