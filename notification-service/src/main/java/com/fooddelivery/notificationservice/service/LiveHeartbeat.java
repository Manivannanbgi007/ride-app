package com.fooddelivery.notificationservice.service;

import java.time.Clock;

import com.fooddelivery.notificationservice.dto.response.LiveMessage;
import com.fooddelivery.notificationservice.web.LiveSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the live connections honest, every few seconds: connections that vanished without closing are closed (so
 * they do not pile up), and the others get a ping and a small heartbeat message, which is how the application
 * notices a connection that died silently and reconnects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveHeartbeat {

    private final LiveSessions sessions;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval:25s}")
    public void beat() {
        int closed = sessions.closeStale();
        if (closed > 0) {
            log.info("Closed {} stale live connection(s)", closed);
        }
        if (sessions.size() == 0) {
            return;
        }
        try {
            sessions.heartbeat(jsonMapper.writeValueAsString(LiveMessage.heartbeat(clock.instant())));
        } catch (JacksonException e) {
            log.warn("Live heartbeat could not be written: {}", e.getMessage());
        }
    }
}
