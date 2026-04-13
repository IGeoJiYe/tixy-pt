package com.tixypt.chatting.support.message.dto;

import java.util.List;

// 커서 기반 메시지 조회 응답
public record SupportMessageSliceResponse(
        List<SupportMessageResponse> messages,

        // 더 과거 메시지가 남아 있는지 여부
        boolean hasNext,

        // 다음 요청에서 beforeMessageId로 넣을 커서 값
        Long nextCursor
) {
}
