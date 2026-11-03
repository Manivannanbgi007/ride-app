package com.fooddelivery.orderservice.entity;

/** Who changed an order (shown in the timeline and in cancellations). */
public enum ActorType {
    CUSTOMER,
    RESTAURANT,
    ADMIN,
    SYSTEM,
    /** The delivery partner (picked up, delivered: reported by delivery-service). */
    DELIVERY_PARTNER
}
