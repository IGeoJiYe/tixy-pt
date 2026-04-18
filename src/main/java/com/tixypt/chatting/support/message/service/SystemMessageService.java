package com.tixypt.chatting.support.message.service;

import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.message.dto.event.MessageEvent;
import com.tixypt.chatting.support.message.policy.SupportSystemMessageTemplate;
import com.tixypt.chatting.support.message.repository.SupportMessageRepository;
import com.tixypt.chatting.support.websocket.SupportEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemMessageService {

    private final SupportMessageRepository supportMessageRepository;
    private final SupportEventDispatcher supportEventDispatcher;

    @Transactional
    public void appendRoomCreatedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.ROOM_CREATED, false);
    }

    @Transactional
    public void appendCounselorClaimedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.COUNSELOR_CLAIMED, true);
    }

    @Transactional
    public void appendCounselorReleasedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.COUNSELOR_RELEASED, true);
    }

    @Transactional
    public void appendSolvedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.ROOM_SOLVED, true);
    }

    @Transactional
    public void appendReopenedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.ROOM_REOPENED, true);
    }

    @Transactional
    public void appendClosedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.ROOM_CLOSED, true);
    }

    @Transactional
    public void appendAutoClosedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.ROOM_AUTO_CLOSED, true);
    }

    @Transactional
    public void appendCounselorRequestedMessage(SupportRoom room) {
        save(room, SupportSystemMessageTemplate.COUNSELOR_REQUESTED, true);
    }

    private void save(SupportRoom room, String content, boolean broadcast) {
        // 시스템 메시지도 일반 메시지처럼 이력에 남기고 lastMessage 포인터를 함께 갱신
        SupportMessage savedMessage = supportMessageRepository.save(SupportMessage.system(room, content));
        room.updateLastMessage(savedMessage.getId(), savedMessage.getCreatedAt());

        if (broadcast) {
            supportEventDispatcher.dispatchMessageAfterCommit(MessageEvent.from(savedMessage));
        }
    }

}
