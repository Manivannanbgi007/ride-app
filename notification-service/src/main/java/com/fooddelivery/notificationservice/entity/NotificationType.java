package com.fooddelivery.notificationservice.entity;

/** What a notification is about. The text of each type is written by NotificationTemplates. */
public enum NotificationType {
    WELCOME,

    RESTAURANT_SUBMITTED,
    RESTAURANT_APPROVED,
    RESTAURANT_REJECTED,
    RESTAURANT_LIVE,
    RESTAURANT_DEACTIVATED,
    RESTAURANT_SUSPENDED,

    ORDER_PLACED,
    ORDER_ACCEPTED,
    ORDER_PREPARING,
    ORDER_READY,
    ORDER_OUT_FOR_DELIVERY,
    ORDER_DELIVERED,
    ORDER_CANCELLED,
    PAYMENT_FAILED,
    REFUND_COMPLETED,

    /** To the restaurant: a new order to accept or reject. */
    NEW_ORDER_FOR_RESTAURANT,
    /** To the restaurant: an order it may be working on was cancelled. */
    ORDER_CANCELLED_FOR_RESTAURANT,

    /** To the customer: who brings the food (a partner accepted the delivery). */
    PARTNER_ON_THE_WAY,
    /** To a delivery partner: a delivery offered to them, to accept or decline. */
    DELIVERY_OFFERED,
    /** To a delivery partner: the order of their delivery was cancelled. */
    DELIVERY_CANCELLED_FOR_PARTNER
}
