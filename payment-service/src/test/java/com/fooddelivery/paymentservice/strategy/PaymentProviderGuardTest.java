package com.fooddelivery.paymentservice.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;

class PaymentProviderGuardTest {

    private final AtomicInteger providerCalls = new AtomicInteger();
    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.ofDefaults();
    // A provider quota of 2 charges per minute, and no waiting for a slot
    private final PaymentProviderGuard guard = new PaymentProviderGuard(RateLimiterRegistry.of(RateLimiterConfig.custom()
            .limitForPeriod(2).limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build()),
            circuitBreakers);

    @Test
    void chargesAboveTheProvidersQuotaAreNotSent() {
        assertThat(guard.charge(this::charged).success()).isTrue();
        assertThat(guard.charge(this::charged).success()).isTrue();

        ProviderResult third = guard.charge(this::charged);

        assertThat(third.success()).isFalse();
        assertThat(third.failureReason()).isEqualTo(PaymentProviderGuard.BUSY);
        assertThat(providerCalls).hasValue(2); // the third never reached the provider: not charged
        // Refunds do not count against the charge quota
        assertThat(guard.refund(this::charged).success()).isTrue();
    }

    @Test
    void whileTheProviderIsFailingNobodyWaitsForIt() {
        circuitBreakers.circuitBreaker(PaymentProviderGuard.PROVIDER).transitionToOpenState();

        assertThat(guard.charge(this::charged).failureReason()).isEqualTo(PaymentProviderGuard.UNAVAILABLE);
        assertThat(guard.refund(this::charged).failureReason()).isEqualTo(PaymentProviderGuard.UNAVAILABLE);
        assertThat(providerCalls).hasValue(0);
    }

    private ProviderResult charged() {
        providerCalls.incrementAndGet();
        return ProviderResult.succeeded("upi-" + providerCalls.get());
    }
}
