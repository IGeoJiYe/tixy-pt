package com.tixypt.chatting.support.read.dto.response;

import com.tixypt.chatting.support.read.dto.event.SupportReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.SupportUnreadSyncEvent;

// 읽음 처리 결과랑 뒤에 전파에 필요한 payload를 함께 묶음
// 서비스에서 계산을 끝내고 controller는 전파만 담당하게 하려고
public record SupportReadReceiptResult(
        boolean updated,
        SupportReadReceiptEvent roomEvent,
        SupportUnreadSyncEvent userQueueEvent
) {
}
