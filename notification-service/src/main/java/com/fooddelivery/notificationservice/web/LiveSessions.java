package com.fooddelivery.notificationservice.web;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import com.fooddelivery.notificationservice.config.RealtimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * The open live connections of this service instance, and the only place that sends to them.
 * <p>
 * Each connection is wrapped so that several threads (the Kafka listeners and the heartbeat) can send to it safely,
 * and so that a client that does not read cannot hold anyone up: past the time or buffer limit it is closed.
 * Connections live in memory, which is why every instance listens to all events (see LiveEventsConsumer).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveSessions {

    private static final int OVER_THE_LIMIT_CODE = 1008;

    /** One open connection. {@code lastSeen} is the last time the client answered anything (milliseconds). */
    private record Connection(WebSocketSession session, LiveClient client, AtomicLong lastSeen, long openedAt) {
    }

    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final RealtimeProperties properties;
    private final Clock clock;

    public LiveSessions(RealtimeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public int size() {
        return connections.size();
    }

    /**
     * Which of these users have at least one open live connection on THIS instance.
     * <p>
     * With several instances each one knows only its own connections, so the answer covers this instance only. A
     * multi-instance deployment needs a shared view, for example a Redis set/TTL key per user refreshed by the
     * heartbeat, or asking every instance and merging the answers.
     */
    public Set<Long> onlineAmong(Collection<Long> userIds) {
        Set<Long> online = new TreeSet<>();
        for (Connection connection : connections) {
            Long userId = connection.client().userId();
            if (userIds.contains(userId)) {
                online.add(userId);
            }
        }
        return online;
    }

    /** Registers a new connection. A user over their limit of open tabs loses the oldest connection. */
    public void add(WebSocketSession session, LiveClient client) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session,
                (int) properties.sendTimeLimit().toMillis(), properties.sendBufferBytes());
        long now = clock.millis();
        connections.add(new Connection(safe, client, new AtomicLong(now), now));

        List<Connection> theirs = connections.stream()
                .filter(connection -> connection.client().userId().equals(client.userId()))
                .sorted(Comparator.comparingLong(Connection::openedAt))
                .toList();
        for (int i = 0; i < theirs.size() - properties.maxSessionsPerUser(); i++) {
            log.info("User {} has more than {} live connections: closing the oldest", client.userId(),
                    properties.maxSessionsPerUser());
            drop(theirs.get(i), new CloseStatus(OVER_THE_LIMIT_CODE, "Too many connections"));
        }
    }

    public void remove(String sessionId) {
        connections.removeIf(connection -> connection.session().getId().equals(sessionId));
    }

    /** The client answered (a pong or a message): the connection is alive. */
    public void touch(String sessionId) {
        connections.stream()
                .filter(connection -> connection.session().getId().equals(sessionId))
                .forEach(connection -> connection.lastSeen().set(clock.millis()));
    }

    /** Sends the text to every connection whose client is allowed to see it. Returns how many received it. */
    public int send(Predicate<LiveClient> audience, String json) {
        int sent = 0;
        for (Connection connection : connections) {
            if (audience.test(connection.client()) && sendTo(connection, json)) {
                sent++;
            }
        }
        return sent;
    }

    /** A restaurant owner's new restaurant: their open connections start receiving its orders. */
    public void grantRestaurant(Long ownerUserId, Long restaurantId) {
        connections.stream()
                .filter(connection -> connection.client().userId().equals(ownerUserId))
                .forEach(connection -> connection.client().addRestaurant(restaurantId));
    }

    /** Pings every connection (browsers answer with a pong by themselves) and sends the small heartbeat message. */
    @SuppressWarnings("checkstyle:IllegalCatch") // a broken connection is dropped; the heartbeat goes on for the others
    public void heartbeat(String heartbeatJson) {
        for (Connection connection : connections) {
            if (sendTo(connection, heartbeatJson)) {
                try {
                    connection.session().sendMessage(new PingMessage());
                } catch (IOException | RuntimeException e) {
                    drop(connection, CloseStatus.SESSION_NOT_RELIABLE);
                }
            }
        }
    }

    /** Closes connections that answered nothing for the idle time (a client that vanished without closing). */
    public int closeStale() {
        long limit = clock.millis() - properties.idleTimeout().toMillis();
        List<Connection> stale = new ArrayList<>();
        for (Connection connection : connections) {
            if (connection.lastSeen().get() < limit) {
                stale.add(connection);
            }
        }
        stale.forEach(connection -> drop(connection, CloseStatus.SESSION_NOT_RELIABLE));
        return stale.size();
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // any failure means this client is gone: drop it, keep the others
    private boolean sendTo(Connection connection, String json) {
        try {
            connection.session().sendMessage(new TextMessage(json));
            return true;
        } catch (IOException | RuntimeException e) {
            // Too slow, closed meanwhile, or broken: this client is gone; the others are not affected
            log.debug("Live connection {} dropped while sending: {}", connection.session().getId(), e.toString());
            drop(connection, CloseStatus.SESSION_NOT_RELIABLE);
            return false;
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // closing an already broken connection may fail in any way
    private void drop(Connection connection, CloseStatus status) {
        connections.remove(connection);
        try {
            connection.session().close(status);
        } catch (IOException | RuntimeException e) {
            log.debug("Live connection {} was already closed", connection.session().getId());
        }
    }
}
