package com.tixypt.chatting.support.websocket;

public class SupportStompDestination {

    public static final String ENDPOINT = "/tixypt/ws/support";
    public static final String PUBLISH_PREFIX = "/tixypt/pub/support/v1";
    public static final String SUBSCRIBE_PREFIX = "/tixypt/sub/support/v1";
    public static final String USER_DESTINATION_PREFIX = "/tixypt/user";
    public static final String BROKER_USER_QUEUE_PREFIX = "/tixypt/queue/support/v1";

    private SupportStompDestination() {
    }
}
