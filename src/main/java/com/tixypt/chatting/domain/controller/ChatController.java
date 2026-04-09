package com.tixypt.chatting.domain.controller;

import java.security.Principal;

import com.tixypt.api.member.entity.Member;
import com.tixypt.chatting.domain.entity.ChatMessage;
import com.tixypt.chatting.domain.entity.ChatRoom;
import com.tixypt.chatting.domain.model.ChatMessageDto;
import com.tixypt.chatting.domain.model.TypingIndicatorDto;
import com.tixypt.chatting.domain.repository.ChatMessageRepository;
import com.tixypt.chatting.domain.repository.ChatRoomRepository;
import com.tixypt.core.config.redis.ChatRedisPublisher;
import com.tixypt.core.config.redis.RedisChatMessage;
import com.tixypt.core.security.interceptor.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRedisPublisher chatRedisPublisher;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(ChatMessageDto dto, Principal principal) {

        Member sender = AuthenticatedUser.fromPrincipal(principal);

        ChatRoom room = chatRoomRepository
                .findById(dto.getRoomId())
                .orElseThrow();

        ChatMessage message = new ChatMessage(sender, room, dto.getContent());
        chatMessageRepository.save(message);

        RedisChatMessage redisMessage = new RedisChatMessage(
                message.getChatRoom().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getContent()
        );

        chatRedisPublisher.publish(room.getId(), redisMessage);

    }

    @MessageMapping("/chat.typing")
    public void typing(TypingIndicatorDto dto, Principal principal) {
        Member user = AuthenticatedUser.fromPrincipal(principal);

        dto.setUserId(user.getId());
        dto.setUserName(user.getName());

        // 타이핑 상태를 해당 채팅방의 다른 사용자들에게 브로드캐스트
        messagingTemplate.convertAndSend("/sub/chat/" + dto.getRoomId() + "/typing", dto);
    }
}