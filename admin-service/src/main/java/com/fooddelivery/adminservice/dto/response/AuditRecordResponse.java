package com.fooddelivery.adminservice.dto.response;

import java.time.Instant;

/**
 * One audit record (the fields the requirements list for an audit record).
 *
 * @param auditId       id in the platform-wide audit log
 * @param service       the service that saved it
 * @param sourceAuditId its id in that service (null for admin-service's own records)
 * @param timestamp     when the admin acted
 */
public record AuditRecordResponse(
        Long auditId,
        String service,
        Long sourceAuditId,
        Long adminUserId,
        String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue,
        String reason,
        Instant timestamp,
        String ipAddress,
        String requestId
) {
}
