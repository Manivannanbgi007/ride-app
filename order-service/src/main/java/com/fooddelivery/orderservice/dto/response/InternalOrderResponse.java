package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;

import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.entity.PaymentStatus;

/**
 * An order for other microservices: who pays, how much and whether it is waiting for payment (payment-service),
 * and where it goes (delivery-service, for orders it has not received an OrderCreated event for).
 */
public record InternalOrderResponse(
        Long id,
        Long customerId,
        Long restaurantId,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        String currency,
        String restaurantName,
        String customerName,
        String customerPhone,
        OrderResponse.Address deliveryAddress
) {
}
