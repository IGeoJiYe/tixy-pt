package com.tixypt.chatting.support.entity;

import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 문의를 만든 고객 아이디
    @Column(name = "customer_user_id", nullable = false)
    private Long customerUserId;

    // 방 잡고 있는 상담원 아이디
    @Column(name = "counselor_user_id")
    private Long counselorUserId;

    // 마지막으로 이 문의방을 담당했던 상담원 userId, 종료나 배정 해제로 현재 담당자가 비어도 종료 이력 조회 기준으로 남겨 둠
    @Column(name = "last_counselor_user_id")
    private Long lastCounselorUserId;

    // 방이 열려 있는지 닫혔는지 보는 상태값
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRoomStatus status;

    // 방에서 가장 마지막으로 저장된 메시지, 방 목록에서 최근 대화 위치를 빠르게 잡기 위해서 따로 들고감
    @Column(name = "last_message_id")
    private Long lastMessageId;

    // 마지막 메시지가 언제 들어왔는지 보는 시간, 방 목록 정렬이나 최근 대화 표시에서 같이 씀
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    // SOLVED 상태로 전환된 시각 자동 종료가 해결 대기 시작 시간을 기준으로 계산하니까 별도 컬럼
    @Column(name = "solved_at")
    private LocalDateTime solvedAt;

    // 고객이 어디까지 읽었는지 기록하는 마지막 메시지 id
    @Column(name = "customer_last_read_message_id")
    private Long customerLastReadMessageId;

    // 상담원이 어디까지 읽었는지 기록하는 마지막 메시지 id
    @Column(name = "counselor_last_read_message_id")
    private Long counselorLastReadMessageId;

    // 고객이 마지막으로 읽음 처리한 시간
    @Column(name = "customer_last_read_at")
    private LocalDateTime customerLastReadAt;

    // 상담원이 마지막으로 읽음 처리한 시각
    @Column(name = "counselor_last_read_at")
    private LocalDateTime counselorLastReadAt;

    // 상담원이 마지막으로 이 방에서 활동한 시각
    // timeout 흐름에서 상담원이 방을 오래 비웠는지 볼 때 쓸 거임
    @Column(name = "counselor_last_active_at")
    private LocalDateTime counselorLastActiveAt;

    @Builder
    private SupportRoom(
            Long customerUserId,
            Long counselorUserId,
            Long lastCounselorUserId,
            SupportRoomStatus status,
            Long lastMessageId,
            LocalDateTime lastMessageAt,
            LocalDateTime solvedAt,
            Long customerLastReadMessageId,
            Long counselorLastReadMessageId,
            LocalDateTime customerLastReadAt,
            LocalDateTime counselorLastReadAt,
            LocalDateTime counselorLastActiveAt
    ) {
        this.customerUserId = customerUserId;
        this.counselorUserId = counselorUserId;
        this.lastCounselorUserId = lastCounselorUserId;
        this.status = status;
        this.lastMessageId = lastMessageId;
        this.lastMessageAt = lastMessageAt;
        this.solvedAt = solvedAt;
        this.customerLastReadMessageId = customerLastReadMessageId;
        this.counselorLastReadMessageId = counselorLastReadMessageId;
        this.customerLastReadAt = customerLastReadAt;
        this.counselorLastReadAt = counselorLastReadAt;
        this.counselorLastActiveAt = counselorLastActiveAt;
    }

    public static SupportRoom open(Long customerUserId, Long counselorUserId) {
        // 문의방 생성 때 기본 상태는 open, counselorUserId가 함께 들어오면 생성과 동시에 담당 배정된 방으로 봄
        return SupportRoom.builder()
                .customerUserId(customerUserId)
                .counselorUserId(counselorUserId)
                .lastCounselorUserId(counselorUserId)
                .status(SupportRoomStatus.OPEN)
                .build();
    }

    public void touchCounselorActivity(LocalDateTime activeAt) {
        // 실제 담당 상담원이 있는 방에서만 활동 시각을 갱신
        if (this.counselorUserId != null) {
            this.counselorLastActiveAt = activeAt;
        }
    }

    public void releaseCounselor() {
        // 배정 해제 시 현재 담당 정보만 비우고 마지막 담당 이력은 유지
        this.counselorUserId = null;
        this.counselorLastReadMessageId = null;
        this.counselorLastReadAt = null;
        this.counselorLastActiveAt = null;
    }

    public void forceAssignCounselor(Long counselorUserId, LocalDateTime activeAt) {
        // 재배정 할 때 현재 담당자랑 마지막 담당 이력을 새 삳담원으로 함께 맞춤
        this.counselorUserId = counselorUserId;
        this.lastCounselorUserId = counselorUserId;
        this.counselorLastReadMessageId = null;
        this.counselorLastReadAt = null;
        this.counselorLastActiveAt = activeAt;
    }

    // 문의방을 최종 종료 상태로 바꿈
    // 이미 CLOSED라면 중복 종료 막고 종료 직전 담당 상담원을 마지막 담당 이력으로 남김
    public boolean close() {
        if (this.status == SupportRoomStatus.CLOSED) {
            return false;
        }

        boolean solvedRoom = this.status == SupportRoomStatus.SOLVED;

        if (this.counselorUserId != null) {
            this.lastCounselorUserId = this.counselorUserId;
        }

        this.status = SupportRoomStatus.CLOSED;
        if (!solvedRoom) {
            this.solvedAt = null;
        }
        this.counselorUserId = null;
        this.counselorLastReadMessageId = null;
        this.counselorLastReadAt = null;
        this.counselorLastActiveAt = null;
        return true;
    }

    // OPEN 상태에서만 해결 대기 상태로 전환
    public boolean solve(LocalDateTime solvedAt) {
        // OPEN 상태에서만 해결 대기 상태로 전환
        if (this.status != SupportRoomStatus.OPEN) {
            return false;
        }

        this.status = SupportRoomStatus.SOLVED;
        this.solvedAt = solvedAt;
        return true;
    }

    // 고객이 다시 메시지를 보내면 SOLVED 방을 다시 OPEN으로 되돌림
    public boolean reopen() {
        if (this.status != SupportRoomStatus.SOLVED) {
            return false;
        }

        this.status = SupportRoomStatus.OPEN;
        this.solvedAt = null;
        return true;
    }

    public void updateLastMessage(Long lastMessageId, LocalDateTime lastMessageAt) {
        // 방 목록 정렬이랑 미리보기에 쓰는 마지막 메시지 정보를 함께 갱신
        this.lastMessageId = lastMessageId;
        this.lastMessageAt = lastMessageAt;
    }

    public boolean markCounselorRead(Long lastReadMessageId, LocalDateTime readAt) {
        // 고객 읽음 커서는 뒤로 가지 않고 앞으로만 움직여야 하기 때문에 더 최신 메시지일 때만 마지막 읽음 위치 갱신
        if (!shouldAdvance(this.counselorLastReadMessageId, lastReadMessageId)) {
            return false;
        }

        this.counselorLastReadMessageId = lastReadMessageId;
        this.counselorLastReadAt = readAt;
        return true;
    }

    public boolean markCustomerRead(Long lastReadMessageId, LocalDateTime readAt) {
        // 상담원 읽음 커서도 똑같이
        if (!shouldAdvance(this.customerLastReadMessageId, lastReadMessageId)) {
            return false;
        }

        this.customerLastReadMessageId = lastReadMessageId;
        this.customerLastReadAt = readAt;
        return true;
    }

    private boolean shouldAdvance(Long currentLastReadMessageId, Long newLastReadMessageId) {
        // 읽음 위치는 과거 메시지로 되돌아가면 안 되니까 현재 값보다 앞선 messageId만 똑바른 갱신으로 봄
        return currentLastReadMessageId == null || newLastReadMessageId > currentLastReadMessageId;
    }
}
