package com.tixypt.chatting.support.room.repository;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // 문의방 수정용 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select room
            from SupportRoom room
            where room.id = :roomId
            """)
    Optional<SupportRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    // 고객 문의 이력을 최근 활동 순으로 조회
    @Query("""
            select room
            from SupportRoom room
            where room.customerUserId = :customerUserId
            order by coalesce(room.lastMessageAt, room.createdAt) desc, room.id desc
            """)
    Slice<SupportRoom> findRoomsForCustomer(@Param("customerUserId") Long customerUserId, Pageable pageable);

    // 상담원이 현재 맡고 있는 진행 중 문의방만 조회
    @Query("""
            select room
            from SupportRoom room
            where room.counselorUserId = :counselorUserId
              and room.status in (
                    com.tixypt.chatting.support.enums.SupportRoomStatus.OPEN,
                    com.tixypt.chatting.support.enums.SupportRoomStatus.SOLVED
              )
            order by coalesce(room.lastMessageAt, room.createdAt) desc, room.id desc
            """)
    Slice<SupportRoom> findAssignedRoomsForCounselor(@Param("counselorUserId") Long counselorUserId, Pageable pageable);


    // 상담원이 마지막으로 담당했던 CLOSED 문의방 목록을 종료 이력으로 조회
    @Query("""
            select room
            from SupportRoom room
            where room.lastCounselorUserId = :counselorUserId
              and room.status = com.tixypt.chatting.support.enums.SupportRoomStatus.CLOSED
            order by room.updatedAt desc, room.id desc
            """)
    Slice<SupportRoom> findClosedRoomsForCounselor(@Param("counselorUserId") Long counselorUserId, Pageable pageable);


    // 아직 상담원이 배정되지 않은 OPEN 문의방만 운영 대기열로 조회
    @Query("""
            select room
            from SupportRoom room
            where room.counselorUserId is null
              and room.status = com.tixypt.chatting.support.enums.SupportRoomStatus.OPEN
            order by
                case when room.customerRequestedCounselorAt is null then 1 else 0 end asc,
                room.customerRequestedCounselorAt asc,
                coalesce(room.lastMessageAt, room.createdAt) desc,
                room.id desc
            """)
    Slice<SupportRoom> findUnassignedOpenRooms(Pageable pageable);


    // 오랫동안 응답이 없는 배정 방을 SUPER_ADMIN 조회로 찾음
    @Query("""
            select room
            from SupportRoom room
            where room.status in (
                    com.tixypt.chatting.support.enums.SupportRoomStatus.OPEN,
                    com.tixypt.chatting.support.enums.SupportRoomStatus.SOLVED
                  )
              and room.counselorUserId is not null
              and room.counselorLastActiveAt is not null
              and room.counselorLastActiveAt <= :cutoff
            order by room.counselorLastActiveAt asc, room.id asc
            """)
    Slice<SupportRoom> findStaleAssignedRooms(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);


    // 미배정 OPEN 문의방 선점
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SupportRoom room
            set room.counselorUserId = :counselorUserId,
                room.counselorLastActiveAt = :claimedAt,
                room.customerRequestedCounselorAt = null
            where room.id = :roomId
              and room.counselorUserId is null
              and room.status = com.tixypt.chatting.support.enums.SupportRoomStatus.OPEN
            """)
    int claimCounselorIfUnassigned(
            @Param("roomId") Long roomId,
            @Param("counselorUserId") Long counselorUserId,
            @Param("claimedAt") LocalDateTime claimedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SupportRoom room
            set room.solvedAt = coalesce(room.lastMessageAt, room.updatedAt, room.createdAt)
            where room.status = com.tixypt.chatting.support.enums.SupportRoomStatus.SOLVED
              and room.solvedAt is null
            """)
    int backfillSolvedAtForSolvedRooms();

    // 자동 종료 대상 SOLVED 문의방을 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select room
            from SupportRoom room
            where room.status = com.tixypt.chatting.support.enums.SupportRoomStatus.SOLVED
              and room.solvedAt is not null
              and room.solvedAt <= :cutoff
            order by room.solvedAt asc, room.id asc
            """)
    List<SupportRoom> findExpiredSolvedRoomsForUpdate(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    Slice<SupportRoom> findAllByOrderByIdDesc(Pageable pageable);

    Slice<SupportRoom> findByStatusOrderByIdDesc(SupportRoomStatus status, Pageable pageable);
}
