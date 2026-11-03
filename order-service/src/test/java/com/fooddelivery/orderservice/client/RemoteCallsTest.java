package com.fooddelivery.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.fooddelivery.orderservice.exception.BusinessRuleException;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** The resilience settings of application.yml, applied to calls that succeed, fail or hang. */
class RemoteCallsTest {

    private static final String SERVICE = "catalog-service";

    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .recordExceptions(RemoteServiceException.class)
            .build());
    private final RemoteCalls remoteCalls = new RemoteCalls(circuitBreakers,
            RetryRegistry.of(RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(10))
                    .retryOnException(new ConnectionFailure()).build()),
            BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build()));

    @Test
    void aRefusedConnectionIsTriedOnceMore() {
        AtomicInteger attempts = new AtomicInteger();

        String answer = remoteCalls.call(SERVICE, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw refused();
            }
            return "menu";
        });

        assertThat(answer).isEqualTo("menu");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void aTimeoutIsNotRepeated() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> remoteCalls.call(SERVICE, () -> {
            attempts.incrementAndGet();
            throw new RemoteServiceException(SERVICE,
                    new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out")));
        })).isInstanceOf(RemoteServiceException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void afterTooManyFailuresTheServiceIsNotCalledForAWhile() {
        AtomicInteger attempts = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> remoteCalls.callOnce(SERVICE, () -> {
                attempts.incrementAndGet();
                throw refused();
            })).isInstanceOf(RemoteServiceException.class);
        }
        assertThat(circuitBreakers.circuitBreaker(SERVICE).getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Fails at once, without calling: still the one exception callers handle
        assertThatThrownBy(() -> remoteCalls.call(SERVICE, () -> {
            attempts.incrementAndGet();
            return "menu";
        })).isInstanceOf(RemoteServiceException.class).hasCauseInstanceOf(CallNotPermittedException.class);
        assertThat(attempts).hasValue(5);
    }

    @Test
    void businessAnswersAreNotFailures() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> remoteCalls.call(SERVICE, () -> {
                throw new BusinessRuleException("Coupon WELCOME100: This coupon has been fully used");
            })).isInstanceOf(BusinessRuleException.class);
        }
        assertThat(circuitBreakers.circuitBreaker(SERVICE).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void callOnceIsNeverRepeated() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> remoteCalls.callOnce(SERVICE, () -> {
            attempts.incrementAndGet();
            throw refused();
        })).isInstanceOf(RemoteServiceException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void aBusyServiceTurnsAwayCallsBeyondItsShare() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> remoteCalls.call(SERVICE, () -> {
                inside.countDown();
                await(release);
                return "slow";
            }));
            inside.await();

            assertThatThrownBy(() -> remoteCalls.call(SERVICE, () -> "fast"))
                    .isInstanceOf(RemoteServiceException.class).hasCauseInstanceOf(BulkheadFullException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static RemoteServiceException refused() {
        return new RemoteServiceException(SERVICE,
                new ResourceAccessException("I/O error", new ConnectException("Connection refused")));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
