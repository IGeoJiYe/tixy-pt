package com.tixypt.chatting.support.room.repository;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    // 고객에게 이미 열려 있는 문의방이 있는지 조회
    Optional<SupportRoom> findByCustomerUserIdAndStatus(Long userId, SupportRoomStatus status);
}
