package com.fooddelivery.orderservice.entity;

public enum PaymentMethod {
    CASH_ON_DELIVERY,
    CARD,
    UPI,
    WALLET;

    /** Online payments must succeed before the restaurant sees the order. */
    public boolean isOnline() {
        return this != CASH_ON_DELIVERY;
    }
}
