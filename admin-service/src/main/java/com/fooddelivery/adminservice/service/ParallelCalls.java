package com.fooddelivery.adminservice.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import com.fooddelivery.adminservice.client.PlatformService;
import com.fooddelivery.adminservice.config.AppProperties;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Runs the remote calls of one dashboard or report request at the same time (one virtual thread each), so the
 * answer takes as long as the slowest service, not the sum of all. A service that fails or does not answer within
 * app.call-timeout leaves its part empty and is named in "unavailable"; the rest is still shown.
 */
@Slf4j
@Component
public class ParallelCalls {

    /** Copies the caller's context (the current observation, hence the trace) to the thread that makes the call. */
    private static final ContextSnapshotFactory CONTEXT_SNAPSHOTS = ContextSnapshotFactory.builder().build();

    private final ExecutorService executor;
    private final Duration timeout;

    public ParallelCalls(@Qualifier("remoteCallExecutor") ExecutorService executor, AppProperties appProperties) {
        this.executor = executor;
        this.timeout = appProperties.callTimeout();
    }

    public Batch batch() {
        return new Batch();
    }

    /** A remote call that was started; read it with {@link Batch#await}. */
    public record Call<T>(PlatformService service, Future<T> future) {
    }

    /** The calls of one request. Used by the request thread only. */
    public final class Batch {

        private final Set<String> unavailable = new TreeSet<>();
        private final long deadline = System.nanoTime() + timeout.toNanos();

        public <T> Call<T> start(PlatformService service, Supplier<T> call) {
            // The request id and the trace travel with the call: its log lines, and the X-Request-Id and traceparent
            // headers of the remote call, belong to the caller's request (one trace across all the services asked)
            Map<String, String> context = MDC.getCopyOfContextMap();
            ContextSnapshot trace = CONTEXT_SNAPSHOTS.captureAll();
            Future<T> future = executor.submit(() -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try (ContextSnapshot.Scope scope = trace.setThreadLocals()) {
                    return call.get();
                } finally {
                    MDC.clear();
                }
            });
            return new Call<>(service, future);
        }

        /** The call's result, or null when it failed or ran out of time (its service is then unavailable). */
        public <T> T await(Call<T> call) {
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                return call.future().get(remaining, TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                log.warn("Part of the answer is missing: {} failed: {}", call.service().serviceName(), e.getCause().getMessage());
            } catch (TimeoutException e) {
                call.future().cancel(true);
                log.warn("Part of the answer is missing: {} did not answer within {}", call.service().serviceName(), timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            unavailable.add(call.service().serviceName());
            return null;
        }

        /** Services that could not be asked, sorted by name; empty when the answer is complete. */
        public List<String> unavailable() {
            return List.copyOf(unavailable);
        }
    }
}
