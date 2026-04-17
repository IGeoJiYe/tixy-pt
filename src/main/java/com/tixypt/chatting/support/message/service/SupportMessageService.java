package com.tixypt.chatting.support.message.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.dto.event.SupportMessageEvent;
import com.tixypt.chatting.support.message.dto.response.SupportMessageResponse;
import com.tixypt.chatting.support.message.dto.response.SupportMessageSliceResponse;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
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
public class SupportMessageService {

    private static final int DEFAULT_MESSAGE_QUERY_LIMIT = 30;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SupportSystemMessageService supportSystemMessageService;
    private final MemberService memberService;
    private final SupportEventDispatcher supportEventDispatcher;


    // 최신 메시지부터 커서 기반으로 조회
    public SupportMessageSliceResponse getMessages(
            Long loginUserId,
            Long roomId,
            Long beforeMessageId,
            Integer size
    ) {
        Member loginUser = memberService.findById(loginUserId);
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

        List<SupportMessageResponse> responses = messages.stream()
                .map(SupportMessageResponse::from)
                .toList();

        Long nextCursor = hasNext && !responses.isEmpty()
                ? responses.get(0).messageId()
                : null;

        return new SupportMessageSliceResponse(responses, hasNext, nextCursor);
    }

    //문의방에 새 메시지 저장하고 고객이 SOLVED 상태에서 다시 메시지를 보내면 다시 OPEN으로 되돌림
    @Transactional
    public SupportMessageEvent sendMessage(Long loginUserId, Long roomId, String content) {
        Member loginUser = memberService.findById(loginUserId);
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
            supportSystemMessageService.appendReopenedMessage(room);
        }

        String normalizedContent = normalizeContent(content);

        SupportMessage savedMessage = supportMessageRepository.save(
                SupportMessage.text(room, loginUserId, senderType(loginUser), normalizedContent)
        );

        room.updateLastMessage(savedMessage.getId(), savedMessage.getCreatedAt());
        SupportMessageEvent event = SupportMessageEvent.from(savedMessage);
        supportEventDispatcher.dispatchMessageAfterCommit(event);
        return event;
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
    private boolean reopenSolvedRoomIfNeeded(Member loginUser, SupportRoom room) {
        if (!isCounselor(loginUser) && room.getStatus() == SupportRoomStatus.SOLVED) {
            return room.reopen();
        }
        return false;
    }

    private SupportMessageSenderType senderType(Member loginUser) {
        return isCounselor(loginUser)
                ? SupportMessageSenderType.COUNSELOR
                : SupportMessageSenderType.USER;
    }
}
