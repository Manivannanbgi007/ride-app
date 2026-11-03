package com.fooddelivery.deliveryservice.entity;

/** What happened to a delivery (one row in its timeline). */
public enum DeliveryEventType {
    CREATED,
    ASSIGNED,
    ACCEPTED,
    /** The partner said no: the delivery is offered to someone else. */
    DECLINED,
    /** The partner did not answer in time: the delivery is offered to someone else. */
    EXPIRED,
    /** An admin moved the delivery to another partner (or back to waiting). */
    REASSIGNED,
    PICKED_UP,
    DELIVERED,
    CANCELLED
}
