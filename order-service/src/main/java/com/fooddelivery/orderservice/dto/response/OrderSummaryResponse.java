package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.entity.PaymentStatus;

/**
 * One row in an order list.
 *
 * @param customerName the customer's name as it was when they ordered (null for orders placed before it was kept),
 *                     so a restaurant can tell its orders apart in the list; the order itself also has it
 * @param items        the ordered dishes (what and how many, as ordered), so a list can show what each order is
 *                     made of without opening it
 */
public record OrderSummaryResponse(
        Long id,
        Long customerId,
        String customerName,
        Long restaurantId,
        String restaurantName,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        int itemCount,
        Instant createdAt,
        List<Item> items
) {

    /** One ordered dish: its menu item, its name and food type as ordered, and how many. */
    public record Item(Long menuItemId, String name, String foodType, int quantity) {
    }
}
