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
    ROOM_ALREADY_CLOSED(HttpStatus.CONFLICT, "SR008", "이미 종료된 문의방입니다."),
    INVALID_MESSAGE_CONTENT(HttpStatus.BAD_REQUEST, "SR004", "유효하지 않은 메시지 내용입니다."),
    INVALID_MESSAGE_CURSOR(HttpStatus.BAD_REQUEST, "SR006", "유효하지 않은 메시지 조회 커서입니다."),
    INVALID_MESSAGE_PAGE_SIZE(HttpStatus.BAD_REQUEST, "SR007", "유효하지 않은 메시지 조회 크기입니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
