package com.tixypt.chatting.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorDto {
    private Long roomId;
    private Long userId;
    private String userName;
    private boolean typing; // true: 입력 중, false: 입력 중지
}