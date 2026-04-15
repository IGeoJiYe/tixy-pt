package com.tixypt.chatting.support.room.service;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.message.service.SupportSystemMessageService;
import com.tixypt.chatting.support.room.dto.event.SupportRoomQueueEvent;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportRoomSolvedAutoCloseService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportSystemMessageService supportSystemMessageService;
    private final SupportEventDispatcher supportEventDispatcher;

    @Value("${support.solved-auto-close-days:7}")
    private long solvedAutoCloseDays;

    // SOLVED 후에 오랫동안 추가 대화가 없는 문의방을 찾아서 자동 종료
    @Transactional
    public int closeExpiredSolvedRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(solvedAutoCloseDays);
        List<SupportRoom> expiredRooms = supportRoomRepository.findExpiredSolvedRooms(cutoff);

        expiredRooms.forEach(room -> {
            if (room.close()) {
                supportSystemMessageService.appendAutoClosedMessage(room);
                supportEventDispatcher.dispatchQueueEvent(SupportRoomQueueEvent.closed(room.getId()));
            }
        });

        if (!expiredRooms.isEmpty()) {
            log.info("해결 대기 후 만료된 문의방 {}건을 자동 종료했습니다.", expiredRooms.size());
        }
        return expiredRooms.size();
    }
}
