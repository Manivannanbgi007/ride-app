package com.fooddelivery.deliveryservice.dto.response;

import java.time.LocalDate;
import java.util.Map;

import com.fooddelivery.deliveryservice.entity.DeliveryStatus;

/**
 * Delivery report for a period (deliveries created in it).
 *
 * @param averageDeliveryMinutes from "food ready" to "delivered", for deliveries completed in the period
 *                               (null when none were completed)
 * @param delayedNow             unfinished deliveries that are late right now, whatever the period
 */
public record DeliveryReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Map<DeliveryStatus, Long> byStatus,
        long total,
        long delivered,
        long cancelled,
        Double averageDeliveryMinutes,
        long delayedNow
) {
}
