package com.fooddelivery.paymentservice.strategy;

import java.math.BigDecimal;

import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;

/**
 * Strategy pattern: everything that differs per payment method lives behind this interface.
 * Adding a new method (for example net banking) means adding one class, without touching the services.
 */
public interface PaymentStrategy {

    PaymentMethod method();

    /**
     * Checks the method-specific details (card number, UPI id, ...) and returns a masked description
     * that is safe to store and show, for example "VISA **** 1111".
     */
    String validateAndDescribe(PayRequest request);

    ProviderResult charge(PayRequest request, BigDecimal amount);

    ProviderResult refund(String providerReference, BigDecimal amount);
}
