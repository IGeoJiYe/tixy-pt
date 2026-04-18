package com.tixypt.chatting.support.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// 문의 채팅 ai 관련 설정값을 바인딩하는 프로퍼티 클래스
// yml 설정 파일에 적힌 값을 자바 객체로 읽어 와서 서비스랑 provider에서 사용
// 지금 ai가 어떤 모드로 동작하는지, RAG를 켰는지, 응답 길이랑 검색 개수는 얼마인지 운영 기준 확인
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "support.ai")
public class AiProperties {

    private Mode mode;
    private boolean ragEnabled;                 // RAG 문맥을 함께 사용할지 여부
    private int recentMessageContextLimit;      // AI한테 함께 전달할 최근 메시지 개수
    private int maxResponseCharacters;          // ai 응답이 너무 길어지지 않도록 제한하는 글자 수
    private int ragTopK;                        // RAG 검색 결과에서 프롬프트에 붙일 최대 문서 개수
    private double ragSimilarityThreshold;      // 벡터 검색 결과를 고를 최소 유사도 기준 0에 가까우면 넓게 가져오고 1에 가까우면 엄격하게 가져옴
    private String ragResourcePattern;          // 로컬 정책 문서를 읽어 올 classpath 패턴 지금은 markdown

    public enum Mode {
        POLICY,         // 규칙 기반 응답만 사용
        PROVIDER,       // 선택한 모델만 사용
        HYBRID          // 모델을 먼저 시도한 뒤에 실패하면 규칙 응답으로 대체
    }
}
