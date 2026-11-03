package com.fooddelivery.deliveryservice.dto.response;

import java.time.Instant;

import com.fooddelivery.deliveryservice.entity.AuditAction;

public record AuditLogResponse(
        Long auditId,
        Long adminUserId,
        AuditAction action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue,
        String reason,
        String ipAddress,
        String requestId,
        Instant timestamp
) {
}
