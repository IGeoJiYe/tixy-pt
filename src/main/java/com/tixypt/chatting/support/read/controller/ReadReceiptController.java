package com.tixypt.chatting.support.read.controller;

import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.read.dto.request.ReadReceiptRequest;
import com.tixypt.chatting.support.read.service.ReadReceiptService;
import com.tixypt.chatting.support.websocket.auth.SupportStompPrincipal;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

// 실시간 읽음 처리용 STOMP 컨트롤러
@Controller
@RequiredArgsConstructor
public class ReadReceiptController {

    private final ReadReceiptService readReceiptService;

    // 사용자가 특정 문의방에서 어디까지 읽었는지 서버에 반영
    @MessageMapping("/rooms/{roomId}/read")
    public void markAsRead(
            @DestinationVariable Long roomId,
            ReadReceiptRequest request,
            Principal principal
    ) {
        SupportStompPrincipal supportPrincipal = SupportStompPrincipal.from(principal);

        if (request == null || request.lastReadMessageId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_READ_RECEIPT);
        }

        LoginUserInfoDto loginUser = new LoginUserInfoDto(
                supportPrincipal.getUserId(),
                supportPrincipal.getRole()
        );

        readReceiptService.markAsRead(
                loginUser,
                supportPrincipal.getName(),
                roomId,
                request.lastReadMessageId()
        );
    }
}
