package com.fooddelivery.deliveryservice.client;

import java.util.function.Supplier;

import com.fooddelivery.deliveryservice.exception.RemoteServiceException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Every call to another service goes through here (Resilience4j; settings under resilience4j.* in application.yml,
 * one instance per target service, e.g. "catalog-service"):
 * <ul>
 *   <li><b>bulkhead</b>: only so many calls to one service at the same time, so a slow service cannot take every
 *       request thread (and with them the requests that do not need it);</li>
 *   <li><b>circuit breaker</b>: after too many failures (or too many very slow answers) the service is left alone for a
 *       while: callers get "unavailable" at once instead of waiting for timeouts, and the service gets room to
 *       recover; a few trial calls then decide whether it is back;</li>
 *   <li><b>retry</b>: one more attempt when the connection itself failed (refused or reset, e.g. a restart). Never
 *       after a timeout, an unknown host or an error answer: those would only make the caller wait longer.</li>
 * </ul>
 * Timeouts are set on the HTTP clients (RestClientConfig). A call that is turned away by the circuit breaker or the
 * bulkhead ends as {@link RemoteServiceException}, like any other failure, so callers handle one exception.
 */
@Component
@RequiredArgsConstructor
public class RemoteCalls {

    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final BulkheadRegistry bulkheads;

    /** A call that is safe to repeat (a read, or a write made idempotent by a key). */
    public <T> T call(String service, Supplier<T> call) {
        return run(service, Retry.decorateSupplier(retries.retry(service), protect(service, call)));
    }

    /** A call that must not be repeated automatically. */
    public <T> T callOnce(String service, Supplier<T> call) {
        return run(service, protect(service, call));
    }

    private <T> Supplier<T> protect(String service, Supplier<T> call) {
        return CircuitBreaker.decorateSupplier(circuitBreakers.circuitBreaker(service),
                Bulkhead.decorateSupplier(bulkheads.bulkhead(service), call));
    }

    private static <T> T run(String service, Supplier<T> decorated) {
        try {
            return decorated.get();
        } catch (CallNotPermittedException | BulkheadFullException e) {
            // Not even tried: the circuit is open, or too many calls are already waiting for this service
            throw new RemoteServiceException(service, e);
        }
    }
}
