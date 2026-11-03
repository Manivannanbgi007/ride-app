package com.fooddelivery.deliveryservice.service;

import java.time.Duration;

import com.fooddelivery.deliveryservice.entity.Delivery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Business metrics of deliveries, for the Grafana dashboards (Prometheus names):
 * <ul>
 *   <li>deliveries_duration_seconds (histogram): from "food ready" to "delivered", so Grafana shows the median and
 *       the 95th percentile;</li>
 *   <li>deliveries_completed_total{outcome="delivered"|"cancelled"}.</li>
 * </ul>
 * Counted only once the transaction commits.
 */
@Component
public class DeliveryMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer deliveryTime;

    public DeliveryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.deliveryTime = Timer.builder("deliveries.duration")
                .description("Time from food ready to delivered")
                .publishPercentileHistogram()
                // Deliveries take minutes, not milliseconds: histogram buckets from 10 s to 2 h
                .minimumExpectedValue(Duration.ofSeconds(10))
                .maximumExpectedValue(Duration.ofHours(2))
                .register(meterRegistry);
    }

    public void delivered(Delivery delivery) {
        Duration duration = delivery.getReadyAt() == null || delivery.getDeliveredAt() == null ? null
                : Duration.between(delivery.getReadyAt(), delivery.getDeliveredAt());
        afterCommit(() -> {
            if (duration != null) {
                deliveryTime.record(duration);
            }
            meterRegistry.counter("deliveries.completed", "outcome", "delivered").increment();
        });
    }

    public void cancelled() {
        afterCommit(() -> meterRegistry.counter("deliveries.completed", "outcome", "cancelled").increment());
    }

    private static void afterCommit(Runnable count) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    count.run();
                }
            });
        } else {
            count.run();
        }
    }
}
