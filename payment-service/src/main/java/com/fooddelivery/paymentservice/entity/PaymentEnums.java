package com.fooddelivery.paymentservice.entity;

/** Small enums of the payment domain, kept together for readability. */
public final class PaymentEnums {

    private PaymentEnums() {
    }

    /** Online methods are charged here. Cash on delivery is collected by the rider (Phase 10). */
    public enum PaymentMethod {
        CARD,
        UPI,
        WALLET,
        CASH_ON_DELIVERY;

        public boolean isOnline() {
            return this != CASH_ON_DELIVERY;
        }
    }

    /**
     * The payment gateway that takes online payments (app.payment.provider): SIMULATED is the built-in test gateway
     * (no money moves; the default), RAZORPAY is Razorpay Checkout (Test Mode or live keys).
     */
    public enum PaymentProvider {
        SIMULATED,
        RAZORPAY
    }

    public enum RefundStatus {
        REQUESTED,
        PROCESSING,
        SUCCESS,
        FAILED
    }

    /** Who asked for a refund. */
    public enum RequesterType {
        ADMIN,
        /** Automatic, for example when a payment arrives for an order that was already cancelled. */
        SYSTEM
    }
}
