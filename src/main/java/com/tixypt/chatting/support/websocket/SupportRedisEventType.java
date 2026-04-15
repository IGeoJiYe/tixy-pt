package com.tixypt.chatting.support.websocket;

// Redis pub/sub으로 흘러가는 문의 채팅 이벤트 종류
// subscriber는 이 값을 보고 어떤 브로드캐스트로 보낼지 결정
public enum SupportRedisEventType {
    MESSAGE,
    READ_ROOM,
    READ_USER,
    QUEUE
}
