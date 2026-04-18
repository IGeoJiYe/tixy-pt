package com.tixypt.chatting.support.ai.config;

import com.tixypt.chatting.support.ai.prompt.AiReplyPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// 문의 채팅 ai에서 사용하는 spring ai 조립하는 지즘
// 이 클래스는 직접 응답을 생성하지 않고 응답 생성에 필요한 걸 스프링 빈으로 준비한다
// 1. 로컬 정책 문서를 담아 둘 VectorStore
// 2. 질문과 관련 있는 문서를 프롬프트에 붙이는 Advisor
// 3. OpenAI ChatModel 위에 system prompt랑 advisor를 올린 ChatClient
// 문서를 읽어서 벡터로 적재하고 -> 필요하면 검색해서 붙이고 -> 그 상태로 모델을 호출하는 게 RAG 흐름
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiAdvisorConfig {

    private final AiProperties aiProperties;

    // 로컬 정책 문서를 벡터 스토어 넣는다
    // VectorStore는 질문과 의미가 비슷한 문서를 찾는 저장소라고 이해하면 쉽다
    // 여기서는 로컬 환경에 맞게 SimpleVectorStore를 사용하고 문서 임베딩은 현재 연결된 EmbeddingModel에 맡긴다
    // support.ai.rag-enable=true일 때만 생성되므로 RAG를 끄면 앱이 더 단순한 일반 ChatClient 흐름으로 동작함
    @Bean
    @ConditionalOnProperty(prefix = "support.ai", name = "rag-enabled", havingValue = "true")
    VectorStore supportAiVectorStore(EmbeddingModel embeddingModel, RagDocumentLoader documentLoader) {
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
                        .topK(aiProperties.getRagTopK())
                        .similarityThreshold(aiProperties.getRagSimilarityThreshold())
                        .build())
                .build();
    }

    // 실제 provider가 사용할 ChatClient 팩토리를 만든다
    // provider 쪽은 openAI 전용 ChatClient를 달라고 요청을 하고
    // system 프롬프트와 advisor를 어떻게 조립할 건지는 이 팩토리가 맡음
    @Bean
    AiChatClientFactory aiChatClientFactory(
            ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
            ObjectProvider<Advisor> advisorProvider
    ) {
        return new AiChatClientFactory(openAiChatModelProvider, advisorProvider, aiProperties);
    }

    // openai ChatClient를 필요 시점에 조립해 주는 팩토리
    public static class AiChatClientFactory {

        private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
        private final ObjectProvider<Advisor> advisorProvider;
        private final AiProperties aiProperties;

        public AiChatClientFactory(
                ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
                ObjectProvider<Advisor> advisorProvider,
                AiProperties aiProperties
        ) {
            this.openAiChatModelProvider = openAiChatModelProvider;
            this.advisorProvider = advisorProvider;
            this.aiProperties = aiProperties;
        }

        // openai용 ChatClient를 반환
        // openai chatModel 빈이 아예 준비되지 않은 경우에는 null 반환
        // 만약에 null이면 지금 openai 쓸 수 없다고 판단하고 fallback 흐름으로 간다
        public ChatClient openAiClient() {
            return build(openAiChatModelProvider.getIfAvailable());
        }

        // 공통 시스템 프롬프트와 선택적으로 advisor를 붙여서 최종으로 ChatClient를 만든다
        // RAG가 꺼져 있으면 시스템 프롬프트만 붙고 켜져 있으면 advisor까지 같이 붙음
        private ChatClient build(ChatModel chatModel) {
            if (chatModel == null) {
                return null;
            }

            ChatClient.Builder builder = ChatClient.builder(chatModel)
                    .defaultSystem(AiReplyPolicy.SYSTEM_PROMPT);

            if (aiProperties.isRagEnabled()) {
                Advisor advisor = advisorProvider.getIfAvailable();
                if (advisor != null) {
                    builder.defaultAdvisors(advisor);
                }
            }

            return builder.build();
        }
    }
}
