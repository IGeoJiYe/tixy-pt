package com.tixypt.chatting.support.room.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// SOLVED 상태 문의방 자동 종료 스케줄러
@Component
@RequiredArgsConstructor
public class RoomSolvedAutoCloseScheduler {

    private final RoomSolvedAutoCloseService roomSolvedAutoCloseService;

    // 만료된 SOLVED 문의방 닫음
    @Scheduled(fixedDelayString = "${support.solved-auto-close-check-ms:3600000}")
    public void closeExpiredSolvedRooms() {
        roomSolvedAutoCloseService.closeExpiredSolvedRooms();
    }
}
