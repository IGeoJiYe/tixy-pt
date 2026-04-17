package com.tixypt.chatting.support.message.repository;

import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // 각 방 별별로 unread 집계 결과 projection
    interface RoomUnreadCountSummary {
        Long getRoomId();

        Long getUnreadCount();
    }

    // 최신 메시지부터 size만큼 가져옴
    List<SupportMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    // 특정 커서 이전 메시지를 최신순으로 가져옴
    List<SupportMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeMessageId, Pageable pageable);

    // 특정 room의 최신 senderType 메시지 한 건을 찾음
    Optional<SupportMessage> findFirstByRoomIdAndSenderTypeOrderByIdDesc(Long roomId, SupportMessageSenderType senderType);

    // 메시지 id가 실제로 문의방에 속해 있는지 확인
    boolean existsByIdAndRoomId(Long id, Long roomId);

    @Query("""
            select count(message)
            from SupportMessage message
            where message.room.id = :roomId
              and message.messageType <> com.tixypt.chatting.support.enums.SupportMessageType.SYSTEM
              and message.id > :lastReadMessageId
              and (
                    message.senderType <> com.tixypt.chatting.support.enums.SupportMessageSenderType.USER
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

    // 고객 방 목록 화면에서 현재 페이지 roomID 기준으로 unread 한 번에 집계
    @Query("""
            select message.room.id as roomId, count(message) as unreadCount
            from SupportMessage message
            join message.room room
            where room.id in :roomIds
              and message.messageType <> com.tixypt.chatting.support.enums.SupportMessageType.SYSTEM
              and message.id > coalesce(room.customerLastReadMessageId, 0)
              and (
                    message.senderType <> com.tixypt.chatting.support.enums.SupportMessageSenderType.USER
                    or message.senderUserId is null
                    or message.senderUserId <> :customerUserId
              )
            group by message.room.id
            """)
    List<RoomUnreadCountSummary> countUnreadForCustomerRooms(
            @Param("roomIds") List<Long> roomIds,
            @Param("customerUserId") Long customerUserId
    );


    @Query("""
            select count(message)
            from SupportMessage message
            where message.room.id = :roomId
              and message.messageType <> com.tixypt.chatting.support.enums.SupportMessageType.SYSTEM
              and message.id > :lastReadMessageId
              and (
                    message.senderType <> com.tixypt.chatting.support.enums.SupportMessageSenderType.COUNSELOR
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


    // 상담원 담당 목록도 현재 페이지 roomId 기준으로 unread를 한 번에 집계
    @Query("""
            select message.room.id as roomId, count(message) as unreadCount
            from SupportMessage message
            join message.room room
            where room.id in :roomIds
              and message.messageType <> com.tixypt.chatting.support.enums.SupportMessageType.SYSTEM
              and message.id > coalesce(room.counselorLastReadMessageId, 0)
              and (
                    message.senderType <> com.tixypt.chatting.support.enums.SupportMessageSenderType.COUNSELOR
                    or message.senderUserId is null
                    or message.senderUserId <> :counselorUserId
              )
            group by message.room.id
            """)
    List<RoomUnreadCountSummary> countUnreadForCounselorRooms(
            @Param("roomIds") List<Long> roomIds,
            @Param("counselorUserId") Long counselorUserId
    );
}
