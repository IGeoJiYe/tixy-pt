package com.tixypt.chatting.support.message.policy;

public class SupportSystemMessageTemplate {

    public static final String ROOM_CREATED = "문의가 접수되었습니다. 상담원이 확인 중입니다.";
    public static final String COUNSELOR_CLAIMED = "상담원이 연결되었습니다.";
    public static final String COUNSELOR_RELEASED = "담당 상담원 연결이 해제되었습니다. 다시 배정될 때까지 잠시만 기다려 주세요.";
    public static final String COUNSELOR_REASSIGNED = "담당 상담원이 변경되었습니다.";
    public static final String ROOM_SOLVED = "답변이 완료되었습니다. 추가 문의가 있으면 메시지를 남겨 주세요.";
    public static final String ROOM_REOPENED = "고객 응답으로 문의가 다시 진행 상태로 변경되었습니다.";
    public static final String ROOM_CLOSED = "문의가 종료되었습니다.";
    public static final String ROOM_AUTO_CLOSED = "추가 응답이 없어 문의가 자동 종료되었습니다.";

    private SupportSystemMessageTemplate() {
    }
}
