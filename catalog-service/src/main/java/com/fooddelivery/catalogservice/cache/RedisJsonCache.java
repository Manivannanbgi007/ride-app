package com.fooddelivery.catalogservice.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.context.ContextSnapshotFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Small JSON cache on Redis with "generations": a cached group (for example one restaurant's menu) has a counter
 * in Redis, and every entry is stored under the counter's current value. A change bumps the counter after its
 * transaction commits; from then on readers look under the new value, and old entries simply expire. Unlike
 * deleting entries, this cannot be undone by a slow reader that read MySQL before the change and writes its
 * outdated copy back a moment later: that copy lands under the old value, which nobody reads any more.
 * <p>
 * Redis is never required, and never makes a request slow:
 * <ul>
 *   <li>every Redis call gets at most {@link #TIME_BUDGET}; a slower or failed call counts as a cache miss and the
 *       caller reads MySQL (reaching a host that is gone can take seconds, mostly in DNS, which no client timeout
 *       covers; the late call finishes on its own virtual thread);</li>
 *   <li>after a failure, reads skip Redis for {@link #RETRY_AFTER}; counter bumps are always tried, so no change is
 *       missed once Redis answers again;</li>
 *   <li>the outage is logged once, and again when Redis is back.</li>
 * </ul>
 */
@Slf4j
@Component
public class RedisJsonCache implements DisposableBean {

    static final Duration TIME_BUDGET = Duration.ofMillis(300);
    static final Duration RETRY_AFTER = Duration.ofSeconds(5);
    /** Copies the caller's context (the current observation, hence the trace) to the thread of a Redis call. */
    private static final ContextSnapshotFactory CONTEXT_SNAPSHOTS = ContextSnapshotFactory.builder().build();

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final ExecutorService redisCalls = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean unavailable = new AtomicBoolean(false);
    private volatile long skipUntilMillis;

    public RedisJsonCache(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    /** Current values of the given counters (0 for one never bumped), or empty if Redis cannot be reached. */
    public Optional<List<Long>> generations(List<String> counterKeys) {
        if (skipping()) {
            return Optional.empty();
        }
        try {
            List<String> values = withinBudget(() -> redisTemplate.opsForValue().multiGet(counterKeys));
            reachable();
            if (values == null) {
                return Optional.of(counterKeys.stream().map(key -> 0L).toList());
            }
            // A counter that was never incremented is generation 0
            return Optional.of(values.stream().map(value -> value == null ? 0L : Long.parseLong(value)).toList());
        } catch (DataAccessException e) {
            unreachable(e);
            return Optional.empty();
        }
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        if (skipping()) {
            return Optional.empty();
        }
        try {
            String json = withinBudget(() -> redisTemplate.opsForValue().get(key));
            reachable();
            return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, type));
        } catch (DataAccessException e) {
            unreachable(e);
            return Optional.empty();
        } catch (JacksonException e) {
            // Written by an older version of the class: treat as a miss, it is replaced below
            log.warn("Cached value could not be read, reloading it. key={} reason={}", key, e.getOriginalMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (skipping()) {
            return;
        }
        try {
            String json = jsonMapper.writeValueAsString(value);
            withinBudget(() -> {
                redisTemplate.opsForValue().set(key, json, ttl);
                return null;
            });
            reachable();
        } catch (DataAccessException e) {
            unreachable(e);
        } catch (JacksonException e) {
            log.warn("Value could not be cached. key={} reason={}", key, e.getOriginalMessage());
        }
    }

    /** Bumps a counter once the current transaction has committed (at once when there is none). */
    public void bumpAfterCommit(String counterKey) {
        Runnable bump = () -> {
            try {
                // A bump that runs late still lands: it keeps going on its own thread
                withinBudget(() -> redisTemplate.opsForValue().increment(counterKey));
                reachable();
            } catch (DataAccessException e) {
                // Entries written before the outage expire with their TTL; Redis restarts empty (no persistence)
                unreachable(e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bump.run();
                }
            });
        } else {
            bump.run();
        }
    }

    @Override
    public void destroy() {
        redisCalls.close();
    }

    /** Runs a Redis call, waiting for it at most {@link #TIME_BUDGET}. */
    private <T> T withinBudget(Callable<T> call) {
        // On its own thread, but in the caller's trace: the Redis command shows up inside the request's trace
        Future<T> result = redisCalls.submit(CONTEXT_SNAPSHOTS.captureAll().wrap(call));
        try {
            return result.get(TIME_BUDGET.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new QueryTimeoutException("Redis did not answer within " + TIME_BUDGET.toMillis() + " ms");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QueryTimeoutException("Interrupted while waiting for Redis");
        }
    }

    private boolean skipping() {
        return System.currentTimeMillis() < skipUntilMillis;
    }

    private void reachable() {
        skipUntilMillis = 0;
        if (unavailable.compareAndSet(true, false)) {
            log.info("Redis is reachable again: caching resumed");
        }
    }

    private void unreachable(DataAccessException e) {
        skipUntilMillis = System.currentTimeMillis() + RETRY_AFTER.toMillis();
        if (unavailable.compareAndSet(false, true)) {
            log.warn("Redis cannot be reached: reading from MySQL until it is back. reason={}", e.getMessage());
        }
    }
}
