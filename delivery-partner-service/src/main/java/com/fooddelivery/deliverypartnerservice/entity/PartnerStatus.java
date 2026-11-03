package com.fooddelivery.deliverypartnerservice.entity;

/**
 * What a delivery partner is doing. delivery-service decides OFFLINE, AVAILABLE and BUSY together with the
 * deliveries (the partner switches between OFFLINE and AVAILABLE; assignment makes them BUSY) and reports every
 * change; SUSPENDED follows the account status an admin sets here.
 */
public enum PartnerStatus {
    OFFLINE,
    AVAILABLE,
    BUSY,
    SUSPENDED
}
