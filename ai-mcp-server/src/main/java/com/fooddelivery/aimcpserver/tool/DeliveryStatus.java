package com.fooddelivery.aimcpserver.tool;

/**
 * The statuses of a delivery (delivery-service), used to validate the status argument of the delivery partner's tools.
 * ASSIGNED is an offer waiting for the partner's answer; ACCEPTED means on the way to the restaurant; PICKED_UP means
 * on the way to the customer.
 */
public enum DeliveryStatus {
    PENDING, ASSIGNED, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED
}
