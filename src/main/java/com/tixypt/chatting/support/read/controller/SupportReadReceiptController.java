package com.tixypt.chatting.support.read.controller;

import com.tixypt.chatting.support.read.dto.request.SupportReadReceiptRequest;
import com.tixypt.chatting.support.read.dto.response.SupportReadReceiptResult;
import com.tixypt.chatting.support.read.service.SupportReadReceiptService;
import com.tixypt.chatting.support.websocket.LocalSupportEventBroadcaster;
import com.tixypt.core.security.interceptor.SupportStompPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class SupportReadReceiptController {

    private final SupportReadReceiptService supportReadReceiptService;
    private final LocalSupportEventBroadcaster localSupportEventBroadcaster;

    @MessageMapping("/rooms/{roomId}/read")
    public void markAsRead(
            @DestinationVariable Long roomId,
            SupportReadReceiptRequest request,
            Principal principal
    ) {
        SupportStompPrincipal supportPrincipal = SupportStompPrincipal.from(principal);

        SupportReadReceiptResult result = supportReadReceiptService.markAsRead(
                supportPrincipal.getUserId(),
                roomId,
                request == null ? null : request.lastReadMessageId()
        );

        localSupportEventBroadcaster.broadcastReadRoom(result.roomEvent());
        localSupportEventBroadcaster.broadcastReadUser(
                supportPrincipal.getName(),
                result.userQueueEvent()
        );
    }
}
