package com.fooddelivery.deliverypartnerservice.entity;

/**
 * Audited admin actions in delivery-partner-service. Same names as when delivery-service owned partners, so the
 * platform-wide audit log reads the same before and after the move.
 */
public enum AuditAction {
    DELIVERY_PARTNER_ACTIVATED,
    DELIVERY_PARTNER_DEACTIVATED
}
