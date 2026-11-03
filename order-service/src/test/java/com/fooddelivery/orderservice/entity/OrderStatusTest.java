package com.fooddelivery.orderservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PAYMENT_PENDING, PLACED",
            "PAYMENT_PENDING, CANCELLED",
            "PLACED, ACCEPTED",
            "PLACED, REJECTED",
            "PLACED, CANCELLED",
            "ACCEPTED, PREPARING",
            "PREPARING, READY_FOR_PICKUP",
            "READY_FOR_PICKUP, OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY, DELIVERED",
            "PREPARING, CANCELLED"
    })
    void allowedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canChangeTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is not allowed")
    @CsvSource({
            "PAYMENT_PENDING, ACCEPTED",   // the restaurant never sees unpaid orders
            "PLACED, PREPARING",           // must be accepted first
            "ACCEPTED, REJECTED",          // too late to reject
            "OUT_FOR_DELIVERY, CANCELLED", // the food is on its way
            "CANCELLED, PLACED",
            "REJECTED, ACCEPTED"
    })
    void forbiddenTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canChangeTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED", "REJECTED"})
    void finishedOrdersCannotChangeAnyMore(OrderStatus finished) {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(finished.canChangeTo(next)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void customersCanCancelOnlyBeforeTheRestaurantAccepts(OrderStatus status) {
        boolean expected = status == OrderStatus.PAYMENT_PENDING || status == OrderStatus.PLACED;
        assertThat(status.isCancellableByCustomer()).isEqualTo(expected);
    }
}
