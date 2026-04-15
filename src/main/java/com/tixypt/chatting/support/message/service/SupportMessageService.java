package com.tixypt.chatting.support.message.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportMessageSenderType;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.entity.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.dto.event.SupportMessageEvent;
import com.tixypt.chatting.support.message.dto.response.SupportMessageResponse;
import com.tixypt.chatting.support.message.dto.response.SupportMessageSliceResponse;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.policy.SupportAccessPolicy;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
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
import static com.tixypt.chatting.support.policy.SupportAccessPolicy.validateRoomAccess;

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

    // 메시지 목록은 최신 메시지부터 size + 1건 조회한 뒤에
    // 응답 직전에 오래된 순으로 뒤집어서 화면에서 그대로 붙일 수 있게 반환
    public SupportMessageSliceResponse getMessages(
            Long loginUserId,
            Long roomId,
            Long beforeMessageId,
            Integer size
    ) {
        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        validateRoomAccess(loginUser, room);

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

    // 메시지 저장 전에 방 접근이 가능한지, 방 상태랑, 발신 가능한 역할인지 순서대로 검증
    // 고객이 SOLVED 상태에서 다시 메시지를 보내면 같은 몬의를 reopened 처리
    @Transactional
    public SupportMessageEvent sendMessage(Long loginUserId, Long roomId, String content) {
        Member loginUser = memberService.findById(loginUserId);
        SupportRoom room = supportRoomRepository.findById(roomId)
                .orElseThrow(() -> new SupportRoomException(SupportRoomErrorCode.ROOM_NOT_FOUND));

        validateRoomAccess(loginUser, room);
        validateRoomWritable(room);
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
        return SupportMessageEvent.from(savedMessage);
    }


    private SupportMessageSenderType senderType(Member loginUser) {
        return isCounselor(loginUser)
                ? SupportMessageSenderType.COUNSELOR
                : SupportMessageSenderType.USER;
    }

    // 공백 메시지를 막고 저장 가능한 본문 길이만 허용해서 실시간 송신 경로에서도 동일한 메시지 입력 규칙 강제
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


    // 종료된 문의방은 이력 조회만 가능하고 새 메시지는 받지 않도록 막아서 닫힌 방 상태가 실시간 송신으로 다시 깨지지 않게 함
    private void validateRoomWritable(SupportRoom room) {
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ALREADY_CLOSED);
        }
    }

    // beforeMessageId가 없으면 최신 페이지를 조회하고 있으면 해당 메시지보다 과거 메시지만 이어서 조회
    private List<SupportMessage> fetchMessages(Long roomId, Long beforeMessageId, PageRequest pageRequest) {
        if (beforeMessageId == null) {
            return supportMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageRequest);
        }
        return supportMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeMessageId, pageRequest);
    }


    // 고객이 해결 대기 상태에서 다시 메시지를 보내면 같은 문의를 reopened 처리
    private boolean reopenSolvedRoomIfNeeded(Member loginUser, SupportRoom room) {
        if (!SupportAccessPolicy.isCounselor(loginUser) && room.getStatus() == SupportRoomStatus.SOLVED) {
            return room.reopen();
        }
        return false;
    }
}
