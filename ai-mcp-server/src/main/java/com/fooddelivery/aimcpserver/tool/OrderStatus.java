package com.fooddelivery.aimcpserver.tool;

/** The statuses of an order (order-service), used to validate the status argument of the order tools. */
public enum OrderStatus {
    PAYMENT_PENDING, PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, REJECTED
}
