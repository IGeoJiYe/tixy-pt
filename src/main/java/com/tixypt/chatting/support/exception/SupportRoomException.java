package com.tixypt.chatting.support.exception;

import com.tixypt.core.exception.BusinessException;
import com.tixypt.core.exception.ErrorCode;

public class SupportRoomException extends BusinessException {

    public SupportRoomException(ErrorCode errorCode) {
        super(errorCode);
    }

}
