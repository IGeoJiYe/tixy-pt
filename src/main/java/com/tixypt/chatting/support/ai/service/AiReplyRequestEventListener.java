package com.tixypt.chatting.support.ai.service;

import com.tixypt.chatting.support.ai.dto.event.AiReplyRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 고객 메시지 저장 후에 자동 ai 응답 생성 이어서 처리
// 저장 실패한 메시지에 대해서 ai 먼저 응답하는 상황을 막고 실제 모델 호출은 비동기로 돌림
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReplyRequestEventListener {

    private final AiReplyService aiReplyService;

    // 커밋이 끝난 고객 메시지만 자동 ai 응답 시도
    @Async("supportAiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AiReplyRequestEvent event) {
        try {
            aiReplyService.createAutoReplyIfEligible(event.roomId(), event.triggerMessageId());
        } catch (RuntimeException exception) {
            log.warn(
                    "자동 AI 응답 생성에 실패했습니다. roomId={}, triggerMessageId={}",
                    event.roomId(),
                    event.triggerMessageId(),
                    exception
            );
        }
    }
}
