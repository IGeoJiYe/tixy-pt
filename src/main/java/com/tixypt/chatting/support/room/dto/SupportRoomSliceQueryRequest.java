package com.tixypt.chatting.support.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportRoomSliceQueryRequest {

    @Min(value = 1, message = "page는 1 이상이어야 합니다.")
    private Integer page = 1;

    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 50, message = "size는 50 이하여야 합니다.")
    private Integer size = 20;
}
