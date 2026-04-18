package com.tixypt.chatting.support.websocket;

import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.read.dto.event.ReadReceiptEvent;
import com.tixypt.chatting.support.read.dto.event.UnreadCountSyncEvent;
import com.tixypt.chatting.support.room.dto.event.RoomQueueEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportEventDispatcher {

    private final SupportStompEventBroadCaster supportStompEventBroadCaster;
    private final ObjectProvider<SupportRedisEventPublisher> supportRedisEventPublisherProvider;

    // 가능한 경우 레디스 펍섭으로 먼저 시도하고
    // publisher가 없거나 실패하면 로컬 브로드캐스트로 바로 fallback
    public void dispatchMessage(MessageEvent event) {
        SupportRedisEventPublisher publisher = supportRedisEventPublisherProvider.getIfAvailable();
        if (publisher == null) {
            supportStompEventBroadCaster.broadcastMessage(event);
            return;
        }

        try {
            publisher.publish(SupportRedisEvent.message(event));
        } catch (RuntimeException exception) {
            log.warn("문의 채팅 메시지 Redis 발행에 실패해 로컬 브로드캐스트로 전환합니다.");
            supportStompEventBroadCaster.broadcastMessage(event);
        }
    }

    public void dispatchMessageAfterCommit(MessageEvent event) {
        runAfterCommit(() -> dispatchMessage(event));
    }

    public void dispatchReadReceipt(
            String userName,
            ReadReceiptEvent roomEvent,
            UnreadCountSyncEvent userEvent
    ) {
        // 방 전체에는 roomEvent를 보내고 현재 사용자 개인 채널에는 unread sync를 보낸다
        SupportRedisEventPublisher publisher = supportRedisEventPublisherProvider.getIfAvailable();
        if (publisher == null) {
            localDispatchReadReceipt(userName, roomEvent, userEvent);
            return;
        }

        try {
            publisher.publish(SupportRedisEvent.readRoom(roomEvent));
            publisher.publish(SupportRedisEvent.readUser(userName, userEvent));
        } catch (RuntimeException exception) {
            log.warn("지원 채팅 읽음 이벤트 Redis 발행에 실패해 로컬 브로드캐스트로 전환합니다.");
            localDispatchReadReceipt(userName, roomEvent, userEvent);
        }
    }

    public void dispatchReadReceiptAfterCommit(
            String userName,
            ReadReceiptEvent roomEvent,
            UnreadCountSyncEvent userEvent
    ) {
        runAfterCommit(() -> dispatchReadReceipt(userName, roomEvent, userEvent));
    }


    // queue 이벤트도 동일하게 레디스 우선 실패하면 로컬 fallback
    public void dispatchQueueEvent(RoomQueueEvent event) {
        SupportRedisEventPublisher publisher = supportRedisEventPublisherProvider.getIfAvailable();
        if (publisher == null) {
            supportStompEventBroadCaster.broadcastQueue(event);
            return;
        }

        try {
            publisher.publish(SupportRedisEvent.queue(event));
        } catch (RuntimeException exception) {
            log.warn("지원 채팅 queue 이벤트 Redis 발행에 실패해 로컬 브로드캐스트로 전환합니다.");
            supportStompEventBroadCaster.broadcastQueue(event);
        }
    }

    public void dispatchQueueEventAfterCommit(RoomQueueEvent event) {
        runAfterCommit(() -> dispatchQueueEvent(event));
    }

    // 레디스를 쓰지 않거나 실패했을 때 로컬로 직접 보냄
    private void localDispatchReadReceipt(
            String userName,
            ReadReceiptEvent roomEvent,
            UnreadCountSyncEvent userEvent
    ) {
        supportStompEventBroadCaster.broadcastReadRoom(roomEvent);
        supportStompEventBroadCaster.broadcastReadUser(userName, userEvent);
    }

    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
