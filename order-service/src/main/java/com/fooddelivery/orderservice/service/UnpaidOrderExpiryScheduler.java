package com.fooddelivery.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the unpaid-order expiry regularly (switched off in tests that run it themselves). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.unpaid-orders.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class UnpaidOrderExpiryScheduler {

    private final UnpaidOrderExpiryService unpaidOrderExpiryService;

    @Scheduled(fixedDelayString = "${app.unpaid-orders.check-interval:60s}",
            initialDelayString = "${app.unpaid-orders.check-interval:60s}")
    public void cancelExpiredOrders() {
        unpaidOrderExpiryService.cancelExpiredOrders();
    }
}
