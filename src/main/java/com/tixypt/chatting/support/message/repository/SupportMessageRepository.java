package com.tixypt.chatting.support.message.repository;

import com.tixypt.chatting.support.entity.SupportMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // 최신 메시지부터 size만큼 가져옴
    List<SupportMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    // 특정 커서 이전 메시지를 최신순으로 가져옴
    List<SupportMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeMessageId, Pageable pageable);

    @Query("""
            select count(message)
            from SupportMessage message
            where message.room.id = :roomId
              and message.id > :lastReadMessageId
              and (
                    message.senderType <> com.tixypt.chatting.support.entity.SupportMessageSenderType.USER
                    or message.senderUserId is null
                    or message.senderUserId <> :customerUserId
              )
            """)
        // 고객 기준 unread 개수를 계산. 마지막으로 읽은 메시지 이후의 메시지 중에서 내가 보낸 고객 메시지는 제외
    long countUnreadForCustomer(
            @Param("roomId") Long roomId,
            @Param("lastReadMessageId") Long lastReadMessageId,
            @Param("customerUserId") Long customerUserId
    );


    @Query("""
            select count(message)
            from SupportMessage message
            where message.room.id = :roomId
              and message.id > :lastReadMessageId
              and (
                    message.senderType <> com.tixypt.chatting.support.entity.SupportMessageSenderType.COUNSELOR
                    or message.senderUserId is null
                    or message.senderUserId <> :counselorUserId
              )
            """)
        // 상담원 기준 unread 개수를 계산 마지막으로 읽은 메시지 이후의 메시지 중에서 내가 보낸 상담원 메시지는 제외
    long countUnreadForCounselor(
            @Param("roomId") Long roomId,
            @Param("lastReadMessageId") Long lastReadMessageId,
            @Param("counselorUserId") Long counselorUserId
    );
}
