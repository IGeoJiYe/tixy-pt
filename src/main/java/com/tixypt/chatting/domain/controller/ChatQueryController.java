package com.tixypt.chatting.domain.controller;

import java.util.List;

import com.tixypt.chatting.domain.model.ChatMessageResponse;
import com.tixypt.chatting.domain.service.ChatQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatQueryController {

    private final ChatQueryService chatQueryService;

    @GetMapping("/messages")
    public List<ChatMessageResponse> getMessages(
            @RequestParam(defaultValue = "50") int size) {
        return chatQueryService.getRecentMessages(size);
    }

    @GetMapping("/messages/before/{id}")
    public List<ChatMessageResponse> getMessagesBefore(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int size) {
        return chatQueryService.getMessagesBefore(id, size);
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<ChatMessageResponse> getMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "50") int size) {
        return chatQueryService.getRecentMessages(roomId, size);
    }
}