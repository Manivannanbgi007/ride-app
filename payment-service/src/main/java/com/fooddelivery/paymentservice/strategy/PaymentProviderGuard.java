package com.fooddelivery.paymentservice.strategy;

import java.util.function.Supplier;

import com.fooddelivery.paymentservice.exception.RemoteServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Protects the payment provider and us from each other (Resilience4j instance "payment-provider", see
 * application.yml):
 * <ul>
 *   <li><b>rate limiter</b> on charges: providers allow so many requests per second; above that they reject or
 *       block the merchant. A charge waits briefly for a slot, otherwise the customer is asked to try again;</li>
 *   <li><b>circuit breaker</b>: while the provider keeps failing, nobody waits for it.</li>
 * </ul>
 * Never retried automatically: a repeated charge could take the money twice. A call turned away here never reached
 * the provider, so its outcome is certain (not charged) and the payment can be marked FAILED with the reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProviderGuard {

    public static final String PROVIDER = "payment-provider";
    static final String BUSY = "The payment provider is busy. Please try again in a moment";
    static final String UNAVAILABLE = "The payment provider is unavailable. Please try again in a few minutes";

    private final RateLimiterRegistry rateLimiters;
    private final CircuitBreakerRegistry circuitBreakers;

    public ProviderResult charge(Supplier<ProviderResult> charge) {
        try {
            return RateLimiter.decorateSupplier(rateLimiters.rateLimiter(PROVIDER),
                    CircuitBreaker.decorateSupplier(circuitBreakers.circuitBreaker(PROVIDER), charge)).get();
        } catch (RequestNotPermitted e) {
            log.warn("Charge not sent: the provider's request limit is reached");
            return ProviderResult.declined(BUSY);
        } catch (CallNotPermittedException e) {
            log.warn("Charge not sent: the provider is failing (circuit open)");
            return ProviderResult.declined(UNAVAILABLE);
        }
    }

    /**
     * Any other call to the payment gateway (Razorpay: create an order, fetch or capture a payment) under the same
     * rate limiter and circuit breaker. A call turned away here never reached the gateway: it fails with
     * {@link RemoteServiceException} (HTTP 503 for the caller) and can simply be tried again.
     */
    public <T> T call(Supplier<T> call) {
        try {
            return RateLimiter.decorateSupplier(rateLimiters.rateLimiter(PROVIDER),
                    CircuitBreaker.decorateSupplier(circuitBreakers.circuitBreaker(PROVIDER), call)).get();
        } catch (RequestNotPermitted e) {
            log.warn("Gateway call not sent: the provider's request limit is reached");
            throw new RemoteServiceException(PROVIDER, new IllegalStateException(BUSY, e));
        } catch (CallNotPermittedException e) {
            log.warn("Gateway call not sent: the provider is failing (circuit open)");
            throw new RemoteServiceException(PROVIDER, new IllegalStateException(UNAVAILABLE, e));
        }
    }

    /** Refunds are rare and must not wait behind the charge quota: only the circuit breaker applies. */
    public ProviderResult refund(Supplier<ProviderResult> refund) {
        try {
            return CircuitBreaker.decorateSupplier(circuitBreakers.circuitBreaker(PROVIDER), refund).get();
        } catch (CallNotPermittedException e) {
            log.warn("Refund not sent: the provider is failing (circuit open)");
            return ProviderResult.declined(UNAVAILABLE);
        }
    }
}
