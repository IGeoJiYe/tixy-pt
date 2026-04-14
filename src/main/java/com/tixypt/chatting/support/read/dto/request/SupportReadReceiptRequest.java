package com.tixypt.chatting.support.read.dto.request;

// 어디까지 읽었는지 마지막 메시지 기준점만 보냄
public record SupportReadReceiptRequest(
        Long lastReadMessageId
) {
}
