package com.tixypt.chatting.support.room.service;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.message.service.SystemMessageService;
import com.tixypt.chatting.support.room.dto.event.RoomQueueEvent;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

// SOLVED 상태 문의방을 일정 시간이 지나면 자동으로 닫는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSolvedAutoCloseService {

    private static final int MIN_BATCH_SIZE = 1;

    private final SupportRoomRepository supportRoomRepository;
    private final SystemMessageService systemMessageService;
    private final SupportEventDispatcher supportEventDispatcher;
    private final TransactionTemplate transactionTemplate;

    @Value("${support.solved-auto-close-days:7}")
    private long solvedAutoCloseDays;

    @Value("${support.solved-auto-close-batch-size:100}")
    private int solvedAutoCloseBatchSize;

    // 자동 종료 대상 SOLVED 문의방을 배치 단위로 닫음
    public int closeExpiredSolvedRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(solvedAutoCloseDays);
        int batchSize = resolveBatchSize();
        int closedCount = 0;
        int backfilledCount = backfillSolvedAtIfNeeded();

        while (true) {
            Integer currentBatchCount = transactionTemplate.execute(status -> closeExpiredSolvedRoomBatch(cutoff, batchSize));
            int processedCount = currentBatchCount == null ? 0 : currentBatchCount;
            closedCount += processedCount;

            if (processedCount < batchSize) {
                break;
            }
        }

        if (closedCount > 0) {
            log.info("해결 대기 만료 문의방 {}건을 자동 종료했습니다.", closedCount);
        }
        if (backfilledCount > 0) {
            log.info("기존 SOLVED 문의방 {}건에 solvedAt 기준 시각을 보정했습니다.", backfilledCount);
        }
        return closedCount;
    }

    private int backfillSolvedAtIfNeeded() {
        Integer backfilledCount = transactionTemplate.execute(status -> supportRoomRepository.backfillSolvedAtForSolvedRooms());
        return backfilledCount == null ? 0 : backfilledCount;
    }

    private int closeExpiredSolvedRoomBatch(LocalDateTime cutoff, int batchSize) {
        List<SupportRoom> expiredRooms = supportRoomRepository.findExpiredSolvedRoomsForUpdate(
                cutoff,
                PageRequest.of(0, batchSize)
        );

        expiredRooms.forEach(this::closeRoomIfNeeded);
        return expiredRooms.size();
    }

    // 문의방 상태를 실제로 닫고 후속 시스템 메시지/운영 이벤트 남김
    private void closeRoomIfNeeded(SupportRoom room) {
        if (room.close()) {
            systemMessageService.appendAutoClosedMessage(room);
            supportEventDispatcher.dispatchQueueEventAfterCommit(RoomQueueEvent.closed(room.getId()));
        }
    }

    private int resolveBatchSize() {
        return Math.max(solvedAutoCloseBatchSize, MIN_BATCH_SIZE);
    }
}
