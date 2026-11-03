package com.fooddelivery.adminservice.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Result of asking every service to send its audit records again.
 *
 * @param republished records each service sent (they arrive through Kafka within seconds; duplicates are ignored)
 * @param unavailable services that could not be asked: run the backfill again later
 */
public record AuditBackfillResponse(Map<String, Long> republished, List<String> unavailable) {
}
