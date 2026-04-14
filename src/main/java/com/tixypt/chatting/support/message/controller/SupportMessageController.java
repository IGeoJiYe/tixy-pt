package com.tixypt.chatting.support.message.controller;

import com.tixypt.chatting.support.message.dto.request.SupportMessageEvent;
import com.tixypt.chatting.support.message.dto.request.SupportSendMessageRequest;
import com.tixypt.chatting.support.message.service.SupportMessageService;
import com.tixypt.chatting.support.websocket.LocalSupportEventBroadcaster;
import com.tixypt.core.security.interceptor.SupportStompPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class SupportMessageController {

    private final SupportMessageService supportMessageService;
    private final LocalSupportEventBroadcaster localSupportEventBroadcaster;

    // STOMP publish 요청을 받아서 현재 로그인 사용자의 메시지로 저장하고 저장 결과를 같은 문의방 구독 채널로 바로 전파
    @MessageMapping("/rooms/{roomId}/messages")
    public void send(
            @DestinationVariable Long roomId,
            SupportSendMessageRequest request,
            Principal principal
    ) {
        SupportStompPrincipal supportPrincipal = SupportStompPrincipal.from(principal);

        SupportMessageEvent event = supportMessageService.sendMessage(
                supportPrincipal.getUserId(),
                roomId,
                request == null ? null : request.content()
        );

        localSupportEventBroadcaster.broadcastMessage(event);
    }
}

