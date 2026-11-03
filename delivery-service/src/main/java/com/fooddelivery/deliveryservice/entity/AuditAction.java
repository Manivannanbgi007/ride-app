package com.fooddelivery.deliveryservice.entity;

/** Audited admin actions in delivery-service. */
public enum AuditAction {
    /** Partners are suspended and reactivated in delivery-partner-service since 1.7.0; kept for the older records. */
    DELIVERY_PARTNER_ACTIVATED,
    /** See {@link #DELIVERY_PARTNER_ACTIVATED}. */
    DELIVERY_PARTNER_DEACTIVATED,
    DELIVERY_REASSIGNED
}
