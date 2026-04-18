package com.tixypt.chatting.support.ai.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.ai.config.AiProperties;
import com.tixypt.chatting.support.ai.dto.AiReplyResponse;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.prompt.AiPromptFactory;
import com.tixypt.chatting.support.ai.provider.AiReplyProvider;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.List;

// AI 선응답 생성하는 것의 전체 흐름을 담당
// AI 호출은 느릴 수 있으니까 먼저 읽기/검증/프롬프트 생성을 끝낸 뒤에 모델 호출하고
// 마지막에 짧은 write 트랜잭션으로 다시 잠가서 메시지 저장
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiReplyService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final MemberService memberService;
    private final AiProperties aiProperties;
    private final AiPromptFactory aiPromptFactory;
    private final AiReplyProvider aiReplyProvider;
    private final SupportEventDispatcher supportEventDispatcher;
    private final TransactionOperations transactionOperations;

    // 1. 현재 방 접근/상태를 잠금 없이 먼저 검증을 하고
    // 2. 최근 대화로 프롬프트를 만든 뒤에 ai 호출하고
    // 마지막 저장에서만 짧게 락 잡아서 ai 메시지 반영
    @Transactional
    public AiReplyResponse createAiReply(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = getRoomOrThrow(roomId);
        validateAiReplyRequest(loginUser, room);

        List<SupportMessage> recentMessages = fetchRecentMessages(roomId);
        String latestCustomerMessage = findLatestCustomerMessage(roomId, recentMessages);
        AiPromptContext promptContext = aiPromptFactory.create(roomId, latestCustomerMessage, recentMessages);
        AiReplyDraft answer = aiReplyProvider.generate(promptContext);

        return transactionOperations.execute(status -> saveAiReply(loginUser, roomId, answer));
    }

    private AiReplyResponse saveAiReply(Member loginUser, Long roomId, AiReplyDraft answer) {
        SupportRoom room = supportRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        validateAiReplyRequest(loginUser, room);

        if (SupportAccessPolicy.isCounselor(loginUser)) {
            room.touchCounselorActivity(LocalDateTime.now());
        }

        SupportMessage aiMessage = supportMessageRepository.save(
                SupportMessage.text(room, null, SupportMessageSenderType.AI, answer.content())
        );

        room.updateLastMessage(aiMessage.getId(), aiMessage.getCreatedAt());
        MessageEvent event = MessageEvent.from(aiMessage);
        supportEventDispatcher.dispatchMessageAfterCommit(event);
        return new AiReplyResponse(event, answer.fallback());
    }

    private void validateAiReplyRequest(Member loginUser, SupportRoom room) {
        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        SupportAccessPolicy.validateRoomWritable(room);
        SupportAccessPolicy.validateParticipantWritable(loginUser);
        validateAiReplyAllowed(room);
    }

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

    private void validateAiReplyAllowed(SupportRoom room) {
        if (room.getCustomerRequestedCounselorAt() != null && room.getCounselorUserId() == null) {
            throw new SupportRoomException(SupportRoomErrorCode.AI_REPLY_BLOCKED_BY_COUNSELOR_REQUEST);
        }
    }
}
