package com.tixypt.chatting.support.room.repository;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    // 고객에게 이미 열려 있는 문의방이 있는지 조회
    Optional<SupportRoom> findByCustomerUserIdAndStatus(Long userId, SupportRoomStatus status);

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
              and room.status = com.tixypt.chatting.support.entity.SupportRoomStatus.OPEN
            order by coalesce(room.lastMessageAt, room.createdAt) desc, room.id desc
            """)
        // 상담원이 현재 맡고 있는 OPEN 문의방만 Slice 조회
    Slice<SupportRoom> findAssignedRoomsForCounselor(@Param("counselorUserId") Long counselorUserId, Pageable pageable);

}
