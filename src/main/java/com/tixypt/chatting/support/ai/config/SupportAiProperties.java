package com.tixypt.chatting.support.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "support.ai")
public class SupportAiProperties {

    private Mode mode = Mode.HYBRID;
    private Provider provider = Provider.OLLAMA;    // mode가 PROVIDER 또는 HYBRID일 때 우선 사용할 provider를 정한다
    private boolean ragEnabled = false;             // RAG 문맥을 함께 사용할지 여부
    private int recentMessageContextLimit = 5;      // AI한테 함께 전달할 최근 메시지 개수
    private int maxResponseCharacters = 300;        // ai 응답이 너무 길어지지 않도록 제한하는 글자 수
    private int ragTopK = 3;                        // RAG 검색 결과에서 프롬프트에 붙일 최대 문서 개수
    private double ragSimilarityThreshold = 0.55;   // 벡터 검색 결과를 고를 최소 유사도 기준 0에 가까우면 넓게 가져오고 1에 가까우면 엄격하게 가져옴
    private String ragResourcePattern = "classpath:/rag/support/*md";   // 로컬 정책 문서를 읽어 올 classpath 패턴

    public enum Mode {
        POLICY,         // 규칙 기반 응답만 사용
        PROVIDER,       // 선택한 모델만 사용
        HYBRID          // 모델을 먼저 시도한 뒤에 실패하면 규칙 응답으로 대체
    }

    public enum Provider {
        OPENAI,
        OLLAMA
    }
}
