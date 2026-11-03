package com.fooddelivery.promotionservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.promotionservice.config.AppProperties;
import com.fooddelivery.promotionservice.entity.RedemptionStatus;
import com.fooddelivery.promotionservice.repository.CouponRedemptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Gives back coupon uses that were reserved but never got an order: the checkout failed after the reservation and
 * order-service could not say so (for example it stopped). Without this, such uses would count forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationCleanupService {

    private static final int BATCH_SIZE = 100;

    private final CouponRedemptionRepository redemptionRepository;
    private final CouponRedemptionService couponRedemptionService;
    private final AppProperties appProperties;
    private final Clock clock;

    /** @return how many reservations were given back */
    public int expireAbandonedReservations() {
        Instant before = clock.instant().minus(appProperties.reservations().expireAfter());
        List<Long> ids = redemptionRepository.findIdsReservedBefore(RedemptionStatus.RESERVED, before,
                PageRequest.of(0, BATCH_SIZE));
        String reason = "No order was placed within " + appProperties.reservations().expireAfter().toMinutes() + " minutes";
        int expired = 0;
        for (Long id : ids) {
            if (couponRedemptionService.expireIfStillReserved(id, reason)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Abandoned coupon reservations given back. count={}", expired);
        }
        return expired;
    }
}
