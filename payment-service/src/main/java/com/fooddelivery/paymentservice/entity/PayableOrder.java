package com.fooddelivery.paymentservice.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * payment-service's copy of an order, from OrderCreated and OrderCancelled events (order-service owns it).
 * Statuses are plain strings, like the events: new order statuses must not break this service.
 */
@Entity
@Table(name = "payable_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayableOrder {

    public static final String AWAITING_PAYMENT = "PAYMENT_PENDING";

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PayableOrder of(Long orderId, Long customerId, Long restaurantId, String paymentMethod,
                                  BigDecimal totalAmount, String currency, String status, Instant now) {
        PayableOrder order = new PayableOrder();
        order.orderId = orderId;
        order.customerId = customerId;
        order.restaurantId = restaurantId;
        order.paymentMethod = paymentMethod;
        order.totalAmount = totalAmount;
        order.currency = currency;
        order.status = status;
        order.updatedAt = now;
        return order;
    }

    /** The order was cancelled or rejected: it can no longer be paid. Final, whatever arrives later. */
    public void close(String finalStatus, Instant now) {
        this.status = finalStatus;
        this.updatedAt = now;
    }
}
