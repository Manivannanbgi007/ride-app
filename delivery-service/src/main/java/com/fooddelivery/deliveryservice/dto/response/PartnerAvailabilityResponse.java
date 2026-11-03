package com.fooddelivery.deliveryservice.dto.response;

import java.util.Map;

import com.fooddelivery.deliveryservice.entity.PartnerStatus;

/** How many partners are available, busy, offline and suspended right now. */
public record PartnerAvailabilityResponse(Map<PartnerStatus, Long> byStatus, long total) {
}
