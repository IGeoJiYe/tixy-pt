package com.tixypt.chatting.support.entity;

import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import com.tixypt.chatting.support.enums.SupportMessageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 메시지가 속한 문의방
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private SupportRoom room;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    // 누가 보낸 메시지인지 구분하는 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SupportMessageSenderType senderType;

    // 메시지 종류
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private SupportMessageType messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SupportMessage text(
            SupportRoom room,
            Long senderUserId,
            SupportMessageSenderType senderType,
            String content
    ) {
        // 지금 가장 자주 쓰는 일반 텍스트 메시지 생성용 팩토리
        // messageType을 매번 밖에서 넘기지 않게 TEXT로 고정해서 만듦
        return SupportMessage.builder()
                .room(room)
                .senderUserId(senderUserId)
                .senderType(senderType)
                .messageType(SupportMessageType.TEXT)
                .content(content)
                .build();
    }

    public static SupportMessage system(SupportRoom room, String content) {
        return SupportMessage.builder()
                .room(room)
                .senderUserId(null)
                .senderType(SupportMessageSenderType.SYSTEM)
                .messageType(SupportMessageType.SYSTEM)
                .content(content)
                .build();
    }

    @Builder
    private SupportMessage(
            SupportRoom room,
            Long senderUserId,
            SupportMessageSenderType senderType,
            SupportMessageType messageType,
            String content
    ) {
        this.room = room;
        this.senderUserId = senderUserId;
        this.senderType = senderType;
        this.messageType = messageType;
        this.content = content;
    }

}
