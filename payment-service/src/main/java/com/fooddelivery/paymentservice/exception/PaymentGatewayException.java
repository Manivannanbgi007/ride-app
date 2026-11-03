package com.fooddelivery.paymentservice.exception;

/**
 * The payment gateway (Razorpay) answered with an error, for example wrong API keys or a payment that cannot be
 * captured. Mapped to HTTP 502 when not handled: the problem is upstream, not in the caller's request.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
