package com.tixypt.chatting.support.ai.config;

import com.tixypt.chatting.support.ai.prompt.SupportAiReplyPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SupportAiAdvisorConfig {

    private final SupportAiProperties supportAiProperties;

    // 로컬 정책 문서를 벡터 스토어 넣는다
    // VectorStore는 질문과 의미가 비슷한 문서를 찾는 저장소라고 이해하면 쉽다
    // 여기서는 로컬 환경에 맞게 SimpleVectorStore를 사용하고 문서 임베딩은 현재 연결된 EmbeddingModel에 맡긴다
    // support.ai.rag-enable=true일 때만 생성되므로 RAG를 끄면 앱이 더 단순한 일반 ChatClient 흐름으로 동작함
    @Bean
    @ConditionalOnProperty(prefix = "support.ai", name = "rag-enabled", havingValue = "true")
    VectorStore supportAiVectorStore(EmbeddingModel embeddingModel, SupportAiDocumentLoader documentLoader) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = documentLoader.load();

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }

        log.info("문의 AI RAG 문서 {}건을 벡터 스토어에 적재했습니다.", documents.size());
        return vectorStore;
    }

    // QuestionAnswerAdvisor는 Spring AI가 제공하는 기본 RAG Advisor다
    // ChatClient가 실제 모델을 호출하기 직전에 현재 질문과 비슷한 문서를 벡터 스토어에서 찾고
    // -> 그 문서를 참고 문맥으로 프롬프트에 덧붙이는 역할을 한다
    // topK와 similarityThreshold는 검색 범위를 조절하는 운영값인데 문서를 너무 많이 뭍이면 프롬프트가 길어지고
    // 너무 엄격하면 필요한 문서를 못 찾을 수 있기 때문에 프로퍼티로 열어 둠
    @Bean
    @ConditionalOnProperty(prefix = "support.ai", name = "rag-enabled", havingValue = "true")
    Advisor supportAiQuestionAnswerAdvisor(VectorStore supportAiVectorStore) {
        return QuestionAnswerAdvisor.builder(supportAiVectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(supportAiProperties.getRagTopK())
                        .similarityThreshold(supportAiProperties.getRagSimilarityThreshold())
                        .build())
                .build();
    }

    // OpenAi/Ollama 공통 ChatClient를 만드는 팩토리를 빈으로 등록한다
    @Bean
    SupportAiChatClientFactory supportAiChatClientFactory(
            ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
            ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
            ObjectProvider<Advisor> advisorProvider
    ) {
        return new SupportAiChatClientFactory(
                openAiChatModelProvider,
                ollamaChatModelProvider,
                advisorProvider,
                supportAiProperties
        );
    }

    // provider별 ChatModel을 받아서 실제 호출 가능한 chatClient로 바꿔 주는 팩토리
    public static class SupportAiChatClientFactory {

        private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
        private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
        private final ObjectProvider<Advisor> advisorProvider;
        private final SupportAiProperties supportAiProperties;

        public SupportAiChatClientFactory(
                ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
                ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
                ObjectProvider<Advisor> advisorProvider,
                SupportAiProperties supportAiProperties
        ) {
            this.openAiChatModelProvider = openAiChatModelProvider;
            this.ollamaChatModelProvider = ollamaChatModelProvider;
            this.advisorProvider = advisorProvider;
            this.supportAiProperties = supportAiProperties;
        }

        // OpenAi ChatModel이 준비된 경우에 ChatClient 반환
        public ChatClient openAiClient() {
            return build(openAiChatModelProvider.getIfAvailable());
        }

        public ChatClient ollamaClient() {
            return build(ollamaChatModelProvider.getIfAvailable());
        }


        // ChatClient를 조립하는 공통 메서트
        // defaultSystem: 모델이 매번 같은 서비스 원칙으로 답하도록 하는 공통 정책 문구
        // defaultAdvisors: RAG가 켜져 있으면 검색 기반 참고 문맥을 자동으로 붙이는 거
        // AI에게 항상 지켜야 할 말투/원칙을 먼저 알려 주고 -> 필요하면 관련 문서를 옆에 붙여 준 다음 -> 그 상태로 모델을 호출하는 준비 과정
        private ChatClient build(ChatModel chatModel) {
            if (chatModel == null) {
                return null;
            }

            ChatClient.Builder builder = ChatClient.builder(chatModel)
                    .defaultSystem(SupportAiReplyPolicy.SYSTEM_PROMPT);

            if (supportAiProperties.isRagEnabled()) {
                Advisor advisor = advisorProvider.getIfAvailable();
                if (advisor != null) {
                    builder.defaultAdvisors(advisor);
                }
            }
            return builder.build();
        }


    }
}
