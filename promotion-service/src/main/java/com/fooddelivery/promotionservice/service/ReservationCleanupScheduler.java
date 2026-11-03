package com.fooddelivery.promotionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the reservation clean-up regularly (switched off in tests that run it themselves). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reservations.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationCleanupScheduler {

    private final ReservationCleanupService reservationCleanupService;

    @Scheduled(fixedDelayString = "${app.reservations.cleanup-interval:60s}")
    public void cleanUp() {
        reservationCleanupService.expireAbandonedReservations();
    }
}
