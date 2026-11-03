package com.fooddelivery.aimcpclient.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Two per-user limits, kept in Redis so they hold across all instances:
 * <ul>
 *   <li>a fixed window: at most N messages per window (INCR, with EXPIRE on the first one);</li>
 *   <li>a concurrency limit: a CPU model answers one question at a time, so a user may only have a few
 *       questions waiting at once (INCR on start, DECR when done, with a safety expiry in case of a crash).</li>
 * </ul>
 * When Redis fails the limits are skipped (fail OPEN, one warning): the assistant must not stop only because
 * a counter store is down; the circuit breaker and the model queue still protect the server.
 */
@Slf4j
@Component
public class ChatRateLimiter {

    static final String BUSY_MESSAGE = "The assistant is busy, please wait for your previous question";
    private static final String WINDOW_KEY = "ai:rl:chat:";
    private static final String IN_FLIGHT_KEY = "ai:rl:inflight:";
    private static final long BUSY_RETRY_SECONDS = 5;

    private final StringRedisTemplate redis;
    private final AppProperties.RateLimit limits;
    private final Duration inFlightSafetyExpiry;
    private final AtomicBoolean redisDown = new AtomicBoolean(false);

    public ChatRateLimiter(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.limits = properties.rateLimit();
        // A crashed instance never decrements: the counter must disappear on its own after the longest possible chat
        this.inFlightSafetyExpiry = properties.agentServer().readTimeout().plusSeconds(60);
    }

    /**
     * Counts one message and one running chat for the user.
     *
     * @return a permit to close when the chat is finished (use try-with-resources)
     * @throws TooManyRequestsException 429 when over a limit
     */
    public Permit acquire(long userId) {
        String inFlightKey = IN_FLIGHT_KEY + userId;
        boolean inFlightHeld = false;
        try {
            Long running = redis.opsForValue().increment(inFlightKey);
            inFlightHeld = true;
            redis.expire(inFlightKey, inFlightSafetyExpiry);
            if (running != null && running > limits.maxInFlight()) {
                release(inFlightKey);
                throw new TooManyRequestsException(BUSY_MESSAGE, BUSY_RETRY_SECONDS, TooManyRequestsException.REASON_BUSY);
            }
            checkWindow(userId, inFlightKey);
            markRedisUp();
            return new Permit(this, inFlightKey);
        } catch (DataAccessException e) {
            if (redisDown.compareAndSet(false, true)) {
                log.warn("Redis is not available: rate limits are not enforced ({})", e.getClass().getSimpleName());
            }
            // Do not leave a counter behind if only the later step failed
            return new Permit(this, inFlightHeld ? inFlightKey : null);
        }
    }

    private void checkWindow(long userId, String inFlightKey) {
        String windowKey = WINDOW_KEY + userId;
        Long count = redis.opsForValue().increment(windowKey);
        if (count != null && count == 1) {
            redis.expire(windowKey, limits.window());
        }
        if (count != null && count > limits.chatMessages()) {
            release(inFlightKey);
            Long ttl = redis.getExpire(windowKey, TimeUnit.SECONDS);
            if (ttl == null || ttl < 0) {
                // A key without expiry would block the user forever (crash between INCR and EXPIRE): repair it
                redis.expire(windowKey, limits.window());
                ttl = limits.window().toSeconds();
            }
            throw new TooManyRequestsException("You have asked too many questions. Please try again in "
                    + Math.max(1, (ttl + 59) / 60) + " minute(s).", ttl, TooManyRequestsException.REASON_RATE_LIMIT);
        }
    }

    private void release(String inFlightKey) {
        try {
            Long left = redis.opsForValue().decrement(inFlightKey);
            if (left != null && left <= 0) {
                redis.delete(inFlightKey);
            }
        } catch (DataAccessException e) {
            log.warn("Could not release the in-flight counter ({})", e.getClass().getSimpleName());
        }
    }

    private void markRedisUp() {
        if (redisDown.compareAndSet(true, false)) {
            log.info("Redis is available again: rate limits are enforced again");
        }
    }

    /** Proof that a chat may run; closing it frees the "in flight" slot. */
    public static final class Permit implements AutoCloseable {

        private final ChatRateLimiter limiter;
        private final String inFlightKey;

        private Permit(ChatRateLimiter limiter, String inFlightKey) {
            this.limiter = limiter;
            this.inFlightKey = inFlightKey;
        }

        @Override
        public void close() {
            if (inFlightKey != null) {
                limiter.release(inFlightKey);
            }
        }
    }
}
