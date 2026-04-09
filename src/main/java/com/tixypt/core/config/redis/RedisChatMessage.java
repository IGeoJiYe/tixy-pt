package com.tixypt.core.config.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RedisChatMessage {
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String content;
}