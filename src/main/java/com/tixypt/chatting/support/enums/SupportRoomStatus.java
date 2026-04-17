package com.tixypt.chatting.support.enums;

public enum SupportRoomStatus {
    // 아직 상담이 진행 중이거나 다시 이어질 수 있는 방 상태
    OPEN,

    // 상담원 기준으로는 답변을 마쳤고 고객 추가 응답만 열어 둔 해결 대기 상태
    SOLVED,

    // 더 이상 진행하지 않는 종료 상태
    CLOSED
}
