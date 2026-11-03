package com.fooddelivery.deliveryservice.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.deliveryservice.entity.PartnerStatus;

/**
 * Delivery partners for the admin dashboard and the delivery report.
 *
 * @param partnersNow       partners per status right now
 * @param online            AVAILABLE or BUSY right now
 * @param waitingForPartner deliveries ready but not accepted by a partner yet, right now
 * @param topPartners       most deliveries completed in the period
 */
public record PartnerStatsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Map<PartnerStatus, Long> partnersNow,
        long online,
        long waitingForPartner,
        List<Partner> topPartners
) {

    /**
     * @param averageDeliveryMinutes from "food ready" to "delivered"
     * @param offersMissed           offers declined or not answered in time
     */
    public record Partner(Long userId, String name, long delivered, Double averageDeliveryMinutes, long offersMissed) {
    }
}
