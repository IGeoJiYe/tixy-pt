package com.tixypt.chatting.support.websocket;

public class SupportStompDestination {

    public static final String ENDPOINT = "/ws/support";
    public static final String PUBLISH_PREFIX = "/pub/support/v1";
    public static final String SUBSCRIBE_PREFIX = "/sub/support/v1";
    public static final String USER_DESTINATION_PREFIX = "/user";
    public static final String BROKER_USER_QUEUE_PREFIX = "/queue/support/v1";

    private SupportStompDestination() {
    }
}
