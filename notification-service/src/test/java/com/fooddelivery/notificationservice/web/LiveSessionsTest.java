package com.fooddelivery.notificationservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.fooddelivery.notificationservice.config.RealtimeProperties;
import com.fooddelivery.notificationservice.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Open connections: the limit per user, stale and slow connections, and that one bad client hurts no one. */
class LiveSessionsTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final RealtimeProperties properties = new RealtimeProperties(true, List.of(), Duration.ofSeconds(25),
            Duration.ofSeconds(75), 2, Duration.ofSeconds(5), 65_536, 100);
    private final LiveSessions sessions = new LiveSessions(properties, clock);

    @Test
    void aUserOverTheirLimitOfOpenTabsLosesTheOldestConnection() throws IOException {
        WebSocketSession first = open("s1");
        WebSocketSession second = open("s2");
        WebSocketSession third = open("s3");

        sessions.add(first, client(7L));
        now.set(now.get().plusSeconds(1));
        sessions.add(second, client(7L));
        now.set(now.get().plusSeconds(1));
        sessions.add(third, client(7L));

        assertThat(sessions.size()).isEqualTo(2);
        verify(first).close(any(CloseStatus.class));
        verify(second, never()).close(any(CloseStatus.class));
        verify(third, never()).close(any(CloseStatus.class));
    }

    @Test
    void theLimitIsPerUser() {
        sessions.add(open("a1"), client(1L));
        sessions.add(open("a2"), client(1L));
        sessions.add(open("b1"), client(2L));
        sessions.add(open("b2"), client(2L));

        assertThat(sessions.size()).isEqualTo(4);
    }

    @Test
    void aConnectionThatAnsweredNothingForTheIdleTimeIsClosed() throws IOException {
        WebSocketSession quiet = open("quiet");
        WebSocketSession alive = open("alive");
        sessions.add(quiet, client(1L));
        sessions.add(alive, client(2L));

        now.set(now.get().plusSeconds(60));
        sessions.touch("alive"); // a pong
        now.set(now.get().plusSeconds(30)); // 90 s since "quiet" last answered, 30 s since "alive"

        assertThat(sessions.closeStale()).isEqualTo(1);
        assertThat(sessions.size()).isEqualTo(1);
        verify(quiet).close(any(CloseStatus.class));
        verify(alive, never()).close(any(CloseStatus.class));
    }

    @Test
    void aClientThatCannotBeWrittenToIsDroppedAndTheOthersStillGetTheMessage() throws IOException {
        WebSocketSession broken = open("broken");
        doThrow(new IOException("connection reset")).when(broken).sendMessage(any());
        WebSocketSession fine = open("fine");
        sessions.add(broken, client(1L));
        sessions.add(fine, client(2L));

        int sent = sessions.send(any -> true, "{}");

        assertThat(sent).isEqualTo(1);
        assertThat(sessions.size()).isEqualTo(1);
        verify(fine).sendMessage(new TextMessage("{}"));
    }

    @Test
    void aMessageGoesOnlyToTheClientsTheAudienceAllows() throws IOException {
        WebSocketSession mine = open("mine");
        WebSocketSession theirs = open("theirs");
        sessions.add(mine, client(7L));
        sessions.add(theirs, client(8L));

        sessions.send(live -> live.userId().equals(7L), "{\"x\":1}");

        verify(mine).sendMessage(new TextMessage("{\"x\":1}"));
        verify(theirs, never()).sendMessage(any());
    }

    @Test
    void aClosedConnectionIsForgotten() {
        sessions.add(open("gone"), client(1L));

        sessions.remove("gone");

        assertThat(sessions.size()).isZero();
    }

    private static LiveClient client(Long userId) {
        return new LiveClient(userId, Role.CUSTOMER, Set.of());
    }

    private static WebSocketSession open(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
