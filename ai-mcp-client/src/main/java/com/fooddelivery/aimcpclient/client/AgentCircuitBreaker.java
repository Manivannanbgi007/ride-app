package com.fooddelivery.aimcpclient.client;

import java.time.Clock;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;

/**
 * A small circuit breaker for ai-mcp-server: after {@code failureThreshold} failures in a row, calls are refused
 * for {@code openFor}, so users get an immediate "unavailable" instead of waiting for a long timeout each time.
 * <p>
 * Hand-written (instead of Resilience4j) because this service only has this one use and the rule is tiny.
 * When the open period is over, calls are let through again; the first failure re-opens it at once, the first
 * success closes it.
 */
@Slf4j
public class AgentCircuitBreaker {

    private final Clock clock;
    private final int failureThreshold;
    private final Duration openFor;

    private int consecutiveFailures;
    private long openUntilMillis;

    public AgentCircuitBreaker(Clock clock, int failureThreshold, Duration openFor) {
        this.clock = clock;
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
    }

    /** True when a call may be made now. */
    public synchronized boolean allowsCalls() {
        return clock.millis() >= openUntilMillis;
    }

    public synchronized void recordSuccess() {
        if (consecutiveFailures >= failureThreshold) {
            log.info("Circuit breaker for ai-mcp-server closed again");
        }
        consecutiveFailures = 0;
        openUntilMillis = 0;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openUntilMillis = clock.millis() + openFor.toMillis();
            log.warn("Circuit breaker for ai-mcp-server open for {}s after {} failures in a row",
                    openFor.toSeconds(), consecutiveFailures);
        }
    }
}
