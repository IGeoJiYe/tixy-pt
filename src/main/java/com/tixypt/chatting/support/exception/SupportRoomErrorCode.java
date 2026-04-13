package com.tixypt.chatting.support.exception;

import com.tixypt.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SupportRoomErrorCode implements ErrorCode {

    // roomId 자체가 없거나 이미 정리된 방을 찾을 때 사용
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "SR001", "문의방을 찾을 수 없습니다."),

    // 현재 로그인 사용자가 해당 문의방을 볼 권한이나 다룰 권한이 없을 때 사용
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SR002", "해당 문의방에 접근할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
