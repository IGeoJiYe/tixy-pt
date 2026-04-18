package com.tixypt.chatting.support.room.dto.response;

import com.tixypt.chatting.support.enums.SupportRoomStatus;

import java.time.LocalDateTime;

public record RequestCounselorResponse(
        Long roomId,
        SupportRoomStatus status,
        Long counselorUserId,
        LocalDateTime customerRequestedCounselorAt,
        boolean requested,
        boolean reopened,
        boolean alreadyRequested,
        boolean alreadyAssigned
) {
}