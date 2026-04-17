package com.tixypt.chatting.support.ai.model;

// ai 선응답 생성 결과
public record AiReplyDraft(
        String content,          // 실제로 저장할 답변 본문
        boolean fallback         // ai가 아니라 규칙 기반 기본 문구로 대체되었는지 확인
) {

    public static AiReplyDraft normal(String content) {
        return new AiReplyDraft(content, false);
    }

    public static AiReplyDraft fallback(String content) {
        return new AiReplyDraft(content, true);
    }
}
