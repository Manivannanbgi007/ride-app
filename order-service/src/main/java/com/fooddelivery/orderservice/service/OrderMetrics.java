package com.fooddelivery.orderservice.service;

import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Business metrics of orders, for the Grafana dashboards (Prometheus names):
 * <ul>
 *   <li>orders_placed_total{payment_method}: new orders;</li>
 *   <li>orders_status_changes_total{status, by}: orders entering a status (DELIVERED, CANCELLED, REJECTED, ...).</li>
 * </ul>
 * Counted only once the transaction commits: a change that was rolled back did not happen.
 */
@Component
@RequiredArgsConstructor
public class OrderMetrics {

    private final MeterRegistry meterRegistry;

    public void placed(Order order) {
        String paymentMethod = order.getPaymentMethod().name();
        afterCommit(() -> meterRegistry.counter("orders.placed", "payment_method", paymentMethod).increment());
    }

    public void statusChanged(OrderStatus status, ActorType changedBy) {
        afterCommit(() -> meterRegistry.counter("orders.status.changes", "status", status.name(), "by", changedBy.name())
                .increment());
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
