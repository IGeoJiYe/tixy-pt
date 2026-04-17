package com.tixypt.chatting.support.ai.provider;

import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.model.AiPromptContext;

// 선응답 생성 방식을 교체 가능하게 만들기 위한 공통 인터페이스
// 지금은 정책 기반 응답이랑 provider 라우팅 응답이 이 인터페이스를 통해서 연결된다
public interface AiReplyProvider {

    // 주어진 대화 문맥을 바탕으로 만듦
    AiReplyDraft generate(AiPromptContext request);
}
