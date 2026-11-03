package com.fooddelivery.paymentservice.repository;

import java.math.BigDecimal;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentStatus;

/** Projection: count and amounts of payments with one status and method. */
public interface PaymentTotals {

    PaymentStatus getStatus();

    PaymentMethod getMethod();

    long getPaymentCount();

    BigDecimal getTotalAmount();

    BigDecimal getRefundedAmount();
}
