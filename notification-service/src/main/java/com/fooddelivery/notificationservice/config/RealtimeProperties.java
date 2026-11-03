package com.fooddelivery.notificationservice.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the live updates (WebSocket) under "app.realtime" (see application.yml).
 *
 * @param enabled            switch the WebSocket endpoint and its Kafka listeners off (the REST APIs are not affected)
 * @param allowedOrigins     web pages that may open a connection (the React application's address)
 * @param heartbeatInterval  how often the server pings every connection and sends it a small "heartbeat" message
 * @param idleTimeout        a connection that answered nothing for this long is stale and is closed
 * @param maxSessionsPerUser browser tabs one user may have open; opening one more closes the oldest
 * @param sendTimeLimit      a client that does not take a message within this time is closed (it cannot hold others up)
 * @param sendBufferBytes    how much may wait for a slow client before it is closed
 * @param recentEventsKept   ids of the latest events remembered, to skip an event Kafka delivers twice
 */
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(@DefaultValue("true") boolean enabled,
                                 @DefaultValue({"http://localhost:5180", "http://127.0.0.1:5180"})
                                 List<String> allowedOrigins,
                                 @DefaultValue("25s") Duration heartbeatInterval,
                                 @DefaultValue("75s") Duration idleTimeout,
                                 @DefaultValue("5") int maxSessionsPerUser,
                                 @DefaultValue("5s") Duration sendTimeLimit,
                                 @DefaultValue("65536") int sendBufferBytes,
                                 @DefaultValue("5000") int recentEventsKept) {
}
