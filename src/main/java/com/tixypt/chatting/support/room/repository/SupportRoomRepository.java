package com.tixypt.chatting.support.room.repository;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    // 고객에게 아직 끝나지 않은 문의가 있으면 OPEN/SOLVED 방을 가장 최근 순으로 다시 찾음
    Optional<SupportRoom> findTopByCustomerUserIdAndStatusInOrderByIdDesc(
            Long customerUserId,
            Collection<SupportRoomStatus> statuses
    );

    @Query("""
            select room
            from SupportRoom room
            where room.customerUserId = :customerUserId
            order by coalesce(room.lastMessageAt, room.createdAt) desc, room.id desc
            """)
        // 고객 문의 이력을 최신 활동 순으로 Slice 조회
    Slice<SupportRoom> findRoomsForCustomer(@Param("customerUserId") Long customerUserId, Pageable pageable);


    @Query("""
            select room
            from SupportRoom room
            where room.counselorUserId = :counselorUserId
              and room.status <> com.tixypt.chatting.support.entity.SupportRoomStatus.CLOSED
            order by coalesce(room.lastMessageAt, room.createdAt) desc, room.id desc
            """)
        // 상담원이 현재 맡고 있는 진행 중 문의방만 조회
        // OPEN뿐 아니라 SOLVED도 아직 같은 문의 흐름 안에 있는 방으로 봄
    Slice<SupportRoom> findAssignedRoomsForCounselor(@Param("counselorUserId") Long counselorUserId, Pageable pageable);


    @Query("""
            select room
            from SupportRoom room
            where room.counselorUserId is null
              and room.status = com.tixypt.chatting.support.entity.SupportRoomStatus.OPEN
            order by coalesce(room.lastMessageAt, room.createdAt) desc, room.id desc
            """)
    // 아직 상담원이 배정되지 않은 OPEN 문의방만 대기열로 Slice 조회함
    // 최근 활동 기준으로 정렬해서 운영 화면에서 최신 문의를 먼저 볼 수 있게 함
    Slice<SupportRoom> findUnassignedOpenRooms(Pageable pageable);

    @Query("""
            select room
            from SupportRoom room
            where room.lastCounselorUserId = :counselorUserId
              and room.status = com.tixypt.chatting.support.entity.SupportRoomStatus.CLOSED
            order by room.updatedAt desc, room.id desc
            """)
    // 상담원이 마지막으로 담당했던 CLOSED 문의방 목록을 Slice 조회함
    // close 때 현재 counselorUserId를 비우고 lastCounselorUserId를 남겨서 내 종료 이력을 만든다
    Slice<SupportRoom> findClosedRoomsForCounselor(@Param("counselorUserId") Long counselorUserId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SupportRoom room
            set room.counselorUserId = :counselorUserId,
                room.counselorLastActiveAt = :claimedAt
            where room.id = :roomId
              and room.counselorUserId is null
              and room.status = com.tixypt.chatting.support.entity.SupportRoomStatus.OPEN
            """)
    // 동시 claim 경쟁을 막기 위해서 조건부 update
    // 아직 미배정된 OPEN 방일 때만 현재 상담원을 배정하고 성공하면 1 반환
    int claimCounselorIfUnassigned(
            @Param("roomId") Long roomId,
            @Param("counselorUserId") Long counselorUserId,
            @Param("claimedAt") LocalDateTime claimedAt
    );

    @Query("""
            select room
            from SupportRoom room
            where room.status <> com.tixypt.chatting.support.entity.SupportRoomStatus.CLOSED
              and room.counselorUserId is not null
              and room.counselorLastActiveAt is not null
              and room.counselorLastActiveAt <= :cutoff
            order by room.counselorLastActiveAt asc, room.id asc
            """)
        // 오랫동안 응답이 없는 배정 방을 SUPER_ADMIN 운영 조회
    Slice<SupportRoom> findStaleAssignedRooms(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Query("""
            select room
            from SupportRoom room
            where room.status = com.tixypt.chatting.support.entity.SupportRoomStatus.SOLVED
              and coalesce(room.lastMessageAt, room.updatedAt, room.createdAt) <= :cutoff
            """)
        // 해결 대기 후 일정 시간 동안 추가 활동이 없는 SOLVED 문의방을 찾음
    List<SupportRoom> findExpiredSolvedRooms(@Param("cutoff") LocalDateTime cutoff);

    Slice<SupportRoom> findAllByOrderByIdDesc(Pageable pageable);

    Slice<SupportRoom> findByStatusOrderByIdDesc(SupportRoomStatus status, Pageable pageable);
}
