package com.tixypt.chatting.support.message.repository;

import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportMessageSenderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // 최신 메시지부터 size만큼 가져옴
    List<SupportMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    // 특정 커서 이전 메시지를 최신순으로 가져옴
    List<SupportMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeMessageId, Pageable pageable);

    // 최근 고객 메시지 1건 같은 조회에 사용
    Optional<SupportMessage> findFirstByRoomIdAndSenderTypeOrderByIdDesc(
            Long roomId,
            SupportMessageSenderType senderType
    );

    // 읽음 처리 요청이 실제로 이 방의 메시지를 가리키는지 검증
    boolean existsByIdAndRoomId(Long id, Long roomId);
}
