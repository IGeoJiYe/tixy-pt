package com.tixypt.chatting.support.read.controller;

import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.read.dto.request.SupportReadReceiptRequest;
import com.tixypt.chatting.support.read.service.SupportReadReceiptService;
import com.tixypt.chatting.support.websocket.auth.SupportStompPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

// 실시간 읽음 처리용 STOMP 컨트롤러
@Controller
@RequiredArgsConstructor
public class SupportReadReceiptController {

    private final SupportReadReceiptService supportReadReceiptService;

    // 사용자가 특정 문의방에서 어디까지 읽었는지 서버에 반영
    @MessageMapping("/rooms/{roomId}/read")
    public void markAsRead(
            @DestinationVariable Long roomId,
            SupportReadReceiptRequest request,
            Principal principal
    ) {
        // 읽음 이벤트도 메시지 전송이랑 똑같이 Principal에서 로그인 사용자 꺼내고 서비스에 위임
        SupportStompPrincipal supportPrincipal = SupportStompPrincipal.from(principal);

        if (request == null || request.lastReadMessageId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_READ_RECEIPT);
        }

        supportReadReceiptService.markAsRead(
                supportPrincipal.getUserId(),
                supportPrincipal.getName(),
                roomId,
                request.lastReadMessageId()
        );
    }
}
