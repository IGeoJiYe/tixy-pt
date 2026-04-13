package com.tixypt.chatting.support.message.service;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.enums.MemberRole;
import com.tixypt.api.member.service.MemberService;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.chatting.support.message.dto.SupportMessageResponse;
import com.tixypt.chatting.support.message.dto.SupportMessageSliceResponse;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.room.repository.SupportRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportMessageService {

    private static final int DEFAULT_MESSAGE_QUERY_LIMIT = 30;

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
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



    // beforeMessageId가 없으면 최신 페이지를 조회하고 있으면 해당 메시지보다 과거 메시지만 이어서 조회
    private List<SupportMessage> fetchMessages(Long roomId, Long beforeMessageId, PageRequest pageRequest) {
        if (beforeMessageId == null) {
            return supportMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageRequest);
        }
        return supportMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeMessageId, pageRequest);
    }


    // 고객은 자신의 문의방만 조회할 수 있고 상담사는 현재 자신이 담당 중인 방만 조회할 수 있음
    private void validateRoomAccess(Member loginUser, SupportRoom room) {
        if (isCounselor(loginUser)) {
            if (!Objects.equals(room.getCounselorUserId(), loginUser.getId())) {
                throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
            }
            return;
        }

        if (!Objects.equals(room.getCustomerUserId(), loginUser.getId())) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }


    private boolean isCounselor(Member loginUser) {
        return loginUser.getRole() == MemberRole.ADMIN;
    }


}
