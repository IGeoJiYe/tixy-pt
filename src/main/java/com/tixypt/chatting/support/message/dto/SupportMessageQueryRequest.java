package com.tixypt.chatting.support.message.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportMessageQueryRequest {

    // 이 값보다 과거 메시지만 조회
    @Positive(message = "beforeMessageId는 1 이상이어야 합니다.")
    private Long beforeMessageId;

    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 100, message = "size는 100 이하여야 합니다.")
    private Integer size = 30;
}
