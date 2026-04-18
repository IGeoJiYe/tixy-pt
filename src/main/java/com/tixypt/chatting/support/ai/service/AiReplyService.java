package com.tixypt.chatting.support.ai.service;

import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.ai.config.AiProperties;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.prompt.AiPromptFactory;
import com.tixypt.chatting.support.ai.provider.AiReplyProvider;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.util.List;

// 고객 메시지 뒤에 자동으로 붙는 ai 1차 응답 생성
// 메시지 저장 후 발행된 이벤트만 이 서비스 호출 모델 호출은 트랜잭션 밖에서 처리하고
// 저장 직전에만 다시 방 상태를 확인
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiReplyService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final AiProperties aiProperties;
    private final AiPromptFactory aiPromptFactory;
    private final AiReplyProvider aiReplyProvider;
    private final SupportEventDispatcher supportEventDispatcher;
    private final TransactionOperations transactionOperations;

    // 고객 메시지 직후에 자동 응답을 붙여도 되는 방에서만 ai 응답 생성
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void createAutoReplyIfEligible(Long roomId, Long triggerMessageId) {
        SupportRoom room = getRoomOrThrow(roomId);
        if (!isAutoReplyBlocked(room, triggerMessageId)) {
            return;
        }

        List<SupportMessage> recentMessages = fetchRecentMessages(roomId);
        String latestCustomerMessage = findLatestCustomerMessage(roomId, recentMessages);
        if (!StringUtils.hasText(latestCustomerMessage)) {
            return;
        }

        AiPromptContext promptContext = aiPromptFactory.create(roomId, latestCustomerMessage, recentMessages);
        AiReplyDraft answer = aiReplyProvider.generate(promptContext);

        transactionOperations.execute(status -> {
            saveAutoAiReply(roomId, triggerMessageId, answer);
            return null;
        });
    }

    // ai 응답이 준비된 뒤에도 현재 방상태가 그대로일 때만 AI 메시지를 저장
    private void saveAutoAiReply(Long roomId, Long triggerMessageId, AiReplyDraft answer) {
        SupportRoom room = supportRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        if (!isAutoReplyBlocked(room, triggerMessageId)) {
            return;
        }

        SupportMessage aiMessage = supportMessageRepository.save(
                SupportMessage.text(room, null, SupportMessageSenderType.AI, answer.content())
        );

        room.updateLastMessage(aiMessage.getId(), aiMessage.getCreatedAt());
        supportEventDispatcher.dispatchMessageAfterCommit(MessageEvent.from(aiMessage));
    }


    // 문의방 조회하고 없으면 예외
    private SupportRoom getRoomOrThrow(Long roomId) {
        return supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));
    }

    // 최신 대화 몇 건만 읽어서 프롬프트 재료로 사용
    private List<SupportMessage> fetchRecentMessages(Long roomId) {
        return supportMessageRepository.findByRoomIdOrderByIdDesc(
                roomId,
                PageRequest.of(0, aiProperties.getRecentMessageContextLimit())
        );
    }

    // fallback이랑 프롬프트 생성 모두 고객이 마지막에 무엇을 물었는지가 제일 중요하니까 최근 목록에서 먼저 찾고
    // 없으면 별도 조회로 마지막 고객 메시지를 보완
    private String findLatestCustomerMessage(Long roomId, List<SupportMessage> recentMessages) {
        return recentMessages.stream()
                .filter(message -> message.getSenderType() == SupportMessageSenderType.USER)
                .map(SupportMessage::getContent)
                .findFirst()
                .orElseGet(() -> supportMessageRepository.findFirstByRoomIdAndSenderTypeOrderByIdDesc(
                                roomId,
                                SupportMessageSenderType.USER
                        )
                        .map(SupportMessage::getContent)
                        .orElse(null));
    }

    // 자동 ai 응답이 붙어도 되는 방 상태인지 판단
    private boolean isAutoReplyBlocked(SupportRoom room, Long triggerMessageId) {
        if (triggerMessageId == null) {
            return false;
        }

        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            return false;
        }

        if (room.getCounselorUserId() != null) {
            return false;
        }

        if (room.getCustomerRequestedCounselorAt() != null) {
            return false;
        }

        return triggerMessageId.equals(room.getLastMessageId());
    }
}
