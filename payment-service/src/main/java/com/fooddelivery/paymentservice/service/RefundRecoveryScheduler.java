package com.fooddelivery.paymentservice.service;

import com.fooddelivery.paymentservice.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Regularly sends refunds that were requested but never reached the provider (switched off in tests). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.refund-recovery.enabled", havingValue = "true", matchIfMissing = true)
public class RefundRecoveryScheduler {

    private final RefundService refundService;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${app.refund-recovery.check-interval:60s}",
            initialDelayString = "${app.refund-recovery.check-interval:60s}")
    public void processStuckRefunds() {
        refundService.processStuckRefunds(appProperties.refundRecovery().stuckAfter());
    }
}
