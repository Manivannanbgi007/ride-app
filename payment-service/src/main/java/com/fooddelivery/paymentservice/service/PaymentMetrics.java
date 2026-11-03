package com.fooddelivery.paymentservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Business metrics of payments, for the Grafana dashboards (Prometheus names):
 * <ul>
 *   <li>payments_completed_total{outcome="success"|"failed", method}: payment results, e.g. the failure rate;</li>
 *   <li>refunds_completed_total{outcome}: refund results.</li>
 * </ul>
 * Called after the result was saved.
 */
@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    public void paymentCompleted(String method, boolean success) {
        meterRegistry.counter("payments.completed", "outcome", outcome(success), "method", method).increment();
    }

    public void refundCompleted(boolean success) {
        meterRegistry.counter("refunds.completed", "outcome", outcome(success)).increment();
    }

    private static String outcome(boolean success) {
        return success ? "success" : "failed";
    }
}
