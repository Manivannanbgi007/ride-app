package com.fooddelivery.apigateway.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Spring Cloud Gateway's Redis limiter, made safe for a Redis outage: requests are never held up by it.
 * <ul>
 *   <li>Redis gets at most {@link #REDIS_TIMEOUT} to answer, otherwise the request is let through. (Connecting to a
 *       host that is gone can take seconds, mostly in DNS, which no client timeout covers; and Spring Data Redis
 *       opens the connection on the calling thread, so the call runs on a separate scheduler.)</li>
 *   <li>After a failure Redis is not asked again for {@link #RETRY_AFTER}: requests pass at once meanwhile.</li>
 *   <li>The outage is logged once, and again when Redis is back (as RateLimitConfig). The parent class logs a stack
 *       trace for every failed call, under this class's name; that logger is switched off in application.yml.</li>
 * </ul>
 * The parent answers "allowed, -1 tokens left" when it could not reach Redis: that is how a failure is recognised.
 */
public class OutageAwareRedisRateLimiter extends RedisRateLimiter {

    // Not this class's name: that logger is switched off (see above), and these lines must stay visible
    @SuppressWarnings("java:S3416")
    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    static final Duration REDIS_TIMEOUT = Duration.ofMillis(300);
    static final Duration RETRY_AFTER = Duration.ofSeconds(5);
    private static final String UNKNOWN_BUDGET = "-1";

    private final String name;
    private final AtomicBoolean redisUnavailable = new AtomicBoolean(false);
    private volatile long bypassUntilMillis;

    public OutageAwareRedisRateLimiter(String name, int replenishRate, long burstCapacity, int requestedTokens) {
        super(replenishRate, burstCapacity, requestedTokens);
        this.name = name;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        if (System.currentTimeMillis() < bypassUntilMillis) {
            return Mono.just(notCounted());
        }
        return super.isAllowed(routeId, id)
                // Opening a Redis connection blocks the calling thread: never let that be a request (event-loop) thread
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(REDIS_TIMEOUT, Mono.fromSupplier(() -> {
                    unavailable();
                    return notCounted();
                }))
                .doOnNext(response -> {
                    if (UNKNOWN_BUDGET.equals(response.getHeaders().get(getRemainingHeader()))) {
                        unavailable();
                    } else if (redisUnavailable.compareAndSet(true, false)) {
                        log.info("Redis is reachable again: {} rate limit applied", name);
                    }
                });
    }

    private Response notCounted() {
        return new Response(true, Map.of(getRemainingHeader(), UNKNOWN_BUDGET));
    }

    private void unavailable() {
        bypassUntilMillis = System.currentTimeMillis() + RETRY_AFTER.toMillis();
        if (redisUnavailable.compareAndSet(false, true)) {
            log.warn("Redis cannot be reached: {} rate limit not applied until it is back", name);
        }
    }
}
