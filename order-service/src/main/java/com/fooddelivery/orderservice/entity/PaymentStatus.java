package com.fooddelivery.orderservice.entity;

/** Payment state as known by order-service. Payment Service (Phase 6) owns the payment details. */
public enum PaymentStatus {
    /** Not paid yet (online payment in progress, or cash due on delivery). */
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}
