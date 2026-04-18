package com.tixypt.chatting.support.exception;

import com.tixypt.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SupportRoomErrorCode implements ErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "SR001", "문의방을 찾을 수 없습니다."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SR002", "해당 문의방에 접근할 수 없습니다."),
    ROOM_ALREADY_CLOSED(HttpStatus.CONFLICT, "SR003", "이미 종료된 문의방입니다."),
    INVALID_MESSAGE_CONTENT(HttpStatus.BAD_REQUEST, "SR004", "유효하지 않은 메시지 내용입니다."),
    INVALID_MESSAGE_CURSOR(HttpStatus.BAD_REQUEST, "SR005", "유효하지 않은 메시지 조회 커서입니다."),
    INVALID_MESSAGE_PAGE_SIZE(HttpStatus.BAD_REQUEST, "SR006", "유효하지 않은 메시지 조회 크기입니다."),
    INVALID_ROOM_ASSIGNMENT(HttpStatus.BAD_REQUEST, "SR007", "유효하지 않은 문의방 배정 요청입니다."),
    INVALID_READ_RECEIPT(HttpStatus.BAD_REQUEST, "SR008", "유요하지 않은 읽음 처리 요청입니다."),
    AI_REPLY_BLOCKED_BY_COUNSELOR_REQUEST(HttpStatus.CONFLICT, "SR009", "상담원 연결 요청이 접수된 문의방에서는 AI 선응답을 생성할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
