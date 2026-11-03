package com.fooddelivery.notificationservice.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the server sends over a live connection, always as one JSON text message.
 *
 * @param kind  HELLO (the connection is open and accepted), HEARTBEAT (still here; sent regularly so a client can
 *              tell a silent, dead connection from a quiet platform) or EVENT
 * @param event the change, for EVENT only
 * @param at    when the server sent it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveMessage(Kind kind, LiveEvent event, Instant at) {

    public enum Kind { HELLO, HEARTBEAT, EVENT }

    public static LiveMessage hello(Instant at) {
        return new LiveMessage(Kind.HELLO, null, at);
    }

    public static LiveMessage heartbeat(Instant at) {
        return new LiveMessage(Kind.HEARTBEAT, null, at);
    }

    public static LiveMessage of(LiveEvent event, Instant at) {
        return new LiveMessage(Kind.EVENT, event, at);
    }
}
