package com.fooddelivery.deliveryservice.dto.response;

/** Internal: how many audit records a service sent again to the platform-wide audit log. */
public record AuditReplayResponse(String service, long republished) {
}
