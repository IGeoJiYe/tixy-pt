package com.tixypt.chatting.domain.service;

import java.util.List;

import com.tixypt.chatting.domain.model.ChatMessageResponse;
import com.tixypt.chatting.domain.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatQueryService {

    private final ChatMessageRepository repository;

    public List<ChatMessageResponse> getRecentMessages(int size) {

        Pageable pageable = PageRequest.of(0, size);

        return repository.findRecentMessages(pageable)
                .stream()
                .map(ChatMessageResponse::new)
                .toList();
    }

    public List<ChatMessageResponse> getMessagesBefore(Long lastMessageId, int size) {
        Pageable pageable = PageRequest.of(0, size);

        return repository.findMessagesBefore(lastMessageId, pageable)
                .stream()
                .map(ChatMessageResponse::new)
                .toList();
    }

    public List<ChatMessageResponse> getRecentMessages(Long roomId, int size) {

        Pageable pageable = PageRequest.of(0, size);

        return repository.findRecentByRoom(roomId, pageable)
                .stream()
                .map(ChatMessageResponse::new)
                .toList();
    }
}