package com.fooddelivery.notificationservice.web;

import java.time.Clock;

import com.fooddelivery.notificationservice.dto.response.LiveMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The live connection's lifecycle: register it when it opens (the identity was decided by
 * {@link LiveHandshakeInterceptor}), keep note that the client is alive, and forget it when it closes.
 * <p>
 * Clients only listen. Anything they send counts as "still here" and is otherwise ignored: nothing a client sends
 * can subscribe it to more data, because who sees what is decided on the server by the identity alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveSocketHandler extends TextWebSocketHandler {

    private final LiveSessions sessions;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        LiveClient client = (LiveClient) session.getAttributes().get(LiveHandshakeInterceptor.CLIENT_ATTRIBUTE);
        if (client == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.add(session, client);
        log.info("Live connection opened: user {} ({}), {} open", client.userId(), client.role(), sessions.size());
        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(LiveMessage.hello(clock.instant()))));
        } catch (JacksonException e) {
            log.warn("Live greeting could not be written: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessions.touch(session.getId());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        sessions.touch(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.debug("Live connection {} transport error: {}", session.getId(), exception.toString());
        session.close(CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("Live connection closed ({}), {} open", status.getCode(), sessions.size());
    }
}
