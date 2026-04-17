package com.tixypt.chatting.support.ai.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.ai.config.SupportAiProperties;
import com.tixypt.chatting.support.ai.dto.SupportAiReplyResponse;
import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.ai.model.AiReplyDraft;
import com.tixypt.chatting.support.ai.prompt.SupportAiPromptFactory;
import com.tixypt.chatting.support.ai.provider.AiReplyProvider;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.dto.event.SupportMessageEvent;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportAiReplyService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final MemberService memberService;
    private final SupportAiProperties supportAiProperties;
    private final SupportAiPromptFactory supportAiPromptFactory;
    private final AiReplyProvider aiReplyProvider;
    private final SupportEventDispatcher supportEventDispatcher;

    // 현재 문의방의 최신 대화를 기준으로 ai 초안을 만들고 그걸 바탕으로 실제 채팅 메시지처럼 저장하고 연결
    @Transactional
    public SupportAiReplyResponse createAiReply(Long loginUserId, Long roomId) {
        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = supportRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        SupportAccessPolicy.validateRoomWritable(room);
        SupportAccessPolicy.validateParticipantWritable(loginUser);

        if (SupportAccessPolicy.isCounselor(loginUser)) {
            room.touchCounselorActivity(LocalDateTime.now());
        }

        List<SupportMessage> recentMessages = fetchRecentMessages(roomId);
        String latestCustomerMessage = findLatestCustomerMessage(roomId, recentMessages);
        AiPromptContext promptContext = supportAiPromptFactory.create(roomId, latestCustomerMessage, recentMessages);
        AiReplyDraft answer = aiReplyProvider.generate(promptContext);

        SupportMessage aiMessage = supportMessageRepository.save(
                SupportMessage.text(room, null, SupportMessageSenderType.AI, answer.content())
        );

        room.updateLastMessage(aiMessage.getId(), aiMessage.getCreatedAt());
        SupportMessageEvent event = SupportMessageEvent.from(aiMessage);
        supportEventDispatcher.dispatchMessageAfterCommit(event);
        return new SupportAiReplyResponse(event, answer.fallback());
    }

    // 최신 대화 몇 건만 읽어서 프롬프트 재료로 사용
    private List<SupportMessage> fetchRecentMessages(Long roomId) {
        return supportMessageRepository.findByRoomIdOrderByIdDesc(
                roomId,
                PageRequest.of(0, supportAiProperties.getRecentMessageContextLimit())
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
}
