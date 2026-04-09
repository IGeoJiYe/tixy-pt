package com.tixypt.chatting.domain.repository;

import java.util.List;
import com.tixypt.chatting.domain.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
    select m
    from ChatMessage m
    join fetch m.sender
    order by m.id desc
    """)
    List<ChatMessage> findRecentMessages(Pageable pageable);

    @Query("""
    select m
    from ChatMessage m
    join fetch m.sender
    where m.id < :lastMessageId
    order by m.id desc
    """)
    List<ChatMessage> findMessagesBefore(
            @Param("lastMessageId") Long lastMessageId,
            Pageable pageable
    );

    @Query("""
    select m
    from ChatMessage m
    join fetch m.sender
    where m.chatRoom.id = :roomId
    order by m.id desc
    """)
    List<ChatMessage> findRecentByRoom(@Param("roomId") Long roomId, Pageable pageable);
}