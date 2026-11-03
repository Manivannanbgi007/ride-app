package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.orderservice.entity.DeliveryAddress;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderLine;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.entity.PaymentStatus;

/** Ready-made orders for unit tests. */
final class OrderFixtures {

    private OrderFixtures() {
    }

    static Order order(Long id, Long customerId, Long restaurantId, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(customerId);
        order.setCustomerName("Priya Sharma");
        order.setRestaurantId(restaurantId);
        order.setRestaurantName("Spice Hub");
        order.setStatus(status);
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.getLines().add(new OrderLine(100L, "Chicken Biryani", "NON_VEG", new BigDecimal("299.00"), 1,
                new BigDecimal("299.00")));
        order.setItemsTotal(new BigDecimal("299.00"));
        order.setDeliveryFee(new BigDecimal("40.00"));
        order.setTaxAmount(new BigDecimal("14.95"));
        order.setTotalAmount(new BigDecimal("353.95"));
        order.setCurrency("INR");
        order.setDeliveryAddress(new DeliveryAddress("12 MG Road", null, "Bengaluru", "Karnataka", "560001", "India",
                null, null));
        order.setCreatedAt(Instant.parse("2026-10-07T07:00:00Z"));
        order.setVersion(0L);
        return order;
    }
}
