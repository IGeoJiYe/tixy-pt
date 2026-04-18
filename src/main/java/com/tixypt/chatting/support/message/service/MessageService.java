package com.tixypt.chatting.support.message.service;

import com.tixypt.chatting.support.ai.dto.event.AiReplyRequestEvent;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.message.dto.response.MessageResponse;
import com.tixypt.chatting.support.message.dto.response.MessageCursorResponse;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import com.tixypt.core.security.dto.LoginUserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.tixypt.chatting.support.policy.SupportAccessPolicy.isCounselor;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private static final int DEFAULT_MESSAGE_QUERY_LIMIT = 30;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SystemMessageService systemMessageService;
    private final SupportEventDispatcher supportEventDispatcher;
    private final ApplicationEventPublisher applicationEventPublisher;


    // 최신 메시지부터 커서 기반으로 조회
    public MessageCursorResponse getMessages(
            LoginUserInfoDto loginUser,
            Long roomId,
            Long beforeMessageId,
            Integer size
    ) {
        SupportRoom room = supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        SupportAccessPolicy.validateRoomAccess(loginUser, room);

        int querySize = size == null ? DEFAULT_MESSAGE_QUERY_LIMIT : size;
        PageRequest pageRequest = PageRequest.of(0, querySize + 1);
        List<SupportMessage> messages = new ArrayList<>(fetchMessages(roomId, beforeMessageId, pageRequest));

        boolean hasNext = messages.size() > querySize;
        if (hasNext) {
            messages.remove(messages.size() - 1);
        }

        Collections.reverse(messages);

        List<MessageResponse> responses = messages.stream()
                .map(MessageResponse::from)
                .toList();

        Long nextCursor = hasNext && !responses.isEmpty()
                ? responses.get(0).messageId()
                : null;

        return new MessageCursorResponse(responses, hasNext, nextCursor);
    }

    //문의방에 새 메시지 저장하고 고객이 SOLVED 상태에서 다시 메시지를 보내면 다시 OPEN으로 되돌림
    @Transactional
    public void sendMessage(LoginUserInfoDto loginUser, Long roomId, String content) {
        SupportRoom room = supportRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        SupportAccessPolicy.validateRoomAccess(loginUser, room);
        SupportAccessPolicy.validateRoomWritable(room);
        SupportAccessPolicy.validateParticipantWritable(loginUser);

        boolean reopened = reopenSolvedRoomIfNeeded(loginUser, room);

        if (SupportAccessPolicy.isCounselor(loginUser)) {
            room.touchCounselorActivity(LocalDateTime.now());
        }

        if (reopened) {
            systemMessageService.appendReopenedMessage(room);
        }

        String normalizedContent = normalizeContent(content);

        SupportMessage savedMessage = supportMessageRepository.save(
                SupportMessage.text(room, loginUser.id(), senderType(loginUser), normalizedContent)
        );

        room.updateLastMessage(savedMessage.getId(), savedMessage.getCreatedAt());

        MessageEvent event = MessageEvent.from(savedMessage);
        supportEventDispatcher.dispatchMessageAfterCommit(event);
        publishAiReplyRequestIfNeeded(loginUser, room, savedMessage);
    }



    // beforeMessageId 유무에 따라서 첫 페이지 또는 다음 페이지 메시지를 일긍ㅁ
    private List<SupportMessage> fetchMessages(Long roomId, Long beforeMessageId, PageRequest pageRequest) {
        if (beforeMessageId == null) {
            return supportMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageRequest);
        }
        return supportMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeMessageId, pageRequest);
    }

    // 메시지 본문 검증
    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_MESSAGE_CONTENT);
        }

        String normalizedContent = content.trim();
        if (!StringUtils.hasText(normalizedContent) || normalizedContent.length() > MAX_CONTENT_LENGTH) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_MESSAGE_CONTENT);
        }
        return normalizedContent;
    }

    // 고객이 SOLVED 문의방에 다시 메시지를 보냈으면 OPEN으로 되돌림
    private boolean reopenSolvedRoomIfNeeded(LoginUserInfoDto loginUser, SupportRoom room) {
        if (!isCounselor(loginUser) && room.getStatus() == SupportRoomStatus.SOLVED) {
            return room.reopen();
        }
        return false;
    }

    // 현재 로그인 사용자를 메시지 senderType으로 변환
    private SupportMessageSenderType senderType(LoginUserInfoDto loginUser) {
        return isCounselor(loginUser)
                ? SupportMessageSenderType.COUNSELOR
                : SupportMessageSenderType.USER;
    }

    // 고객 메시지 저장 완료 뒤에 자동 ai 응답이 필요한 경우에 내부 이벤트
    private void publishAiReplyRequestIfNeeded(LoginUserInfoDto loginUser, SupportRoom room, SupportMessage savedMessage) {
        if (SupportAccessPolicy.isCounselor(loginUser)) {
            return;
        }

        if (room.getCounselorUserId() != null || room.getCustomerRequestedCounselorAt() != null) {
            return;
        }

        applicationEventPublisher.publishEvent(new AiReplyRequestEvent(room.getId(), savedMessage.getId()));
    }
}
