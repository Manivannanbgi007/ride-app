package com.fooddelivery.orderservice.repository;

import java.math.BigDecimal;

import com.fooddelivery.orderservice.entity.OrderStatus;

/** Projection: number of orders and their total value, for one status. */
public interface StatusTotals {

    OrderStatus getStatus();

    long getOrderCount();

    BigDecimal getTotalAmount();
}
