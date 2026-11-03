package com.fooddelivery.adminservice.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.PartnerRow;

/**
 * Delivery report (delivery-service): deliveries started in the period, and partner performance.
 *
 * @param failed                 deliveries cancelled before reaching the customer
 * @param averageDeliveryMinutes from "food ready" to "delivered"
 * @param delayedNow             deliveries running late right now
 * @param partnersOnline         partners available or on a delivery right now
 * @param topPartners            most deliveries completed in the period
 */
public record DeliveryReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Long total,
        Long completed,
        Long failed,
        Double averageDeliveryMinutes,
        Long delayedNow,
        Map<String, Long> byStatus,
        Long partnersOnline,
        Long waitingForPartner,
        List<PartnerRow> topPartners,
        List<String> unavailable
) {
}
