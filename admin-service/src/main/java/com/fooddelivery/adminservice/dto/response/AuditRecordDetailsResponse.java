package com.fooddelivery.adminservice.dto.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An audit record with what else happened in the same admin request.
 *
 * @param receivedAt  when the copy reached the platform-wide audit log
 * @param sameRequest other records with the same requestId (at most 20), oldest first
 */
public record AuditRecordDetailsResponse(
        @JsonProperty("record") AuditRecordResponse auditRecord,
        Instant receivedAt,
        List<AuditRecordResponse> sameRequest
) {
}
