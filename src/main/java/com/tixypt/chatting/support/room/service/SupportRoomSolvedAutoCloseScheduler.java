package com.tixypt.chatting.support.room.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportRoomSolvedAutoCloseScheduler {

    private final SupportRoomSolvedAutoCloseService supportRoomSolvedAutoCloseService;

    @Scheduled(fixedDelayString = "${support.solved-auto-close-check-ms:3600000}")
    public void closeExpiredSolvedRooms() {
        supportRoomSolvedAutoCloseService.closeExpiredSolvedRooms();
    }
}
