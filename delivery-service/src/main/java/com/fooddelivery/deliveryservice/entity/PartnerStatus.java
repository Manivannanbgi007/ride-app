package com.fooddelivery.deliveryservice.entity;

/**
 * Availability of a delivery partner. The partner switches between OFFLINE and AVAILABLE; the system makes
 * them BUSY while they have a delivery; only an admin suspends (deactivates) or reactivates a partner.
 */
public enum PartnerStatus {
    OFFLINE,
    AVAILABLE,
    BUSY,
    SUSPENDED
}
