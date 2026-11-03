package com.fooddelivery.adminservice.dto.response;

/** How many records the audit log has for one action of one service (for filter lists). */
public record AuditActionCount(String service, String action, long records) {
}
