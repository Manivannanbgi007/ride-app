package com.fooddelivery.deliverypartnerservice.entity;

import java.time.Instant;

/**
 * What delivery-service reports about a partner's work (DeliveryPartnerStatusChanged).
 *
 * @param sourceVersion       version of delivery-service's partner record: an older report never overwrites a newer one
 * @param status              OFFLINE, AVAILABLE, BUSY or SUSPENDED
 * @param reason              why, when the system decided (e.g. an offer not answered in time)
 * @param currentDeliveryId   the delivery the partner is on (BUSY only)
 * @param availableSince      when the partner last became available (queue order)
 * @param completedDeliveries deliveries completed so far
 */
public record WorkStatus(long sourceVersion,
                         PartnerStatus status,
                         String reason,
                         Long currentDeliveryId,
                         Instant availableSince,
                         int completedDeliveries) {
}
