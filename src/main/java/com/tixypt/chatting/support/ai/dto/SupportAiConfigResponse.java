package com.tixypt.chatting.support.ai.dto;

// 운영 확인 화면에서 현재 AI 설정을 한눈에 읽기 쉽게 보여 주기 위한 응답
// Spring AI 공식 흐름 기준으로 실제 호출 준비가 되었는지 RAG가 켜져 있는지
// Advisor와 VectorStore가 붙을 수 있는 상태인지까지 함께 전달
public record SupportAiConfigResponse(
        String mode,
        String provider,
        boolean ragEnabled,
        boolean ragAdvisorReady,
        boolean ragVectorStoreReady,
        int ragDocumentCount,
        int ragTopK,
        double ragSimilarityThreshold,
        int recentMessageContextLimit,
        int maxResponseCharacters,
        boolean openAiReady,
        boolean ollamaReady,
        String openAiModel,
        String ollamaModel,
        String ollamaBaseUrl
) {
}
