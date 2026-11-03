package com.fooddelivery.deliveryservice.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * delivery-service's copy of what an order's delivery needs, from OrderCreated (or order-service's internal
 * API for an order whose event has not arrived). Never changes: an order's customer and address are fixed.
 * The payment method is a plain string, like in the events.
 */
@Entity
@Table(name = "order_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDetails implements Persistable<Long> {

    public static final String CASH_ON_DELIVERY = "CASH_ON_DELIVERY";

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "restaurant_name", length = 150)
    private String restaurantName;

    @Embedded
    @AttributeOverride(name = "line1", column = @Column(name = "dropoff_line1"))
    @AttributeOverride(name = "line2", column = @Column(name = "dropoff_line2"))
    @AttributeOverride(name = "city", column = @Column(name = "dropoff_city"))
    @AttributeOverride(name = "state", column = @Column(name = "dropoff_state"))
    @AttributeOverride(name = "postalCode", column = @Column(name = "dropoff_postal_code"))
    @AttributeOverride(name = "country", column = @Column(name = "dropoff_country"))
    @AttributeOverride(name = "latitude", column = @Column(name = "dropoff_latitude", precision = 9, scale = 6))
    @AttributeOverride(name = "longitude", column = @Column(name = "dropoff_longitude", precision = 9, scale = 6))
    private Address dropoffAddress;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @SuppressWarnings({"checkstyle:ParameterNumber", "java:S107"}) // the fields of the order event, copied as they are
    public static OrderDetails of(Long orderId, Long customerId, String customerName, String customerPhone,
                                  Long restaurantId, String restaurantName, Address dropoffAddress,
                                  String paymentMethod, BigDecimal totalAmount, String currency, Instant now) {
        OrderDetails details = new OrderDetails();
        details.orderId = orderId;
        details.customerId = customerId;
        details.customerName = customerName;
        details.customerPhone = customerPhone;
        details.restaurantId = restaurantId;
        details.restaurantName = restaurantName;
        details.dropoffAddress = dropoffAddress;
        details.paymentMethod = paymentMethod;
        details.totalAmount = totalAmount;
        details.currency = currency;
        details.receivedAt = now;
        return details;
    }

    /** What the partner collects at the door: the total for cash on delivery, nothing for online payments. */
    public BigDecimal cashToCollect() {
        return CASH_ON_DELIVERY.equals(paymentMethod) ? totalAmount : BigDecimal.ZERO.setScale(2);
    }

    @Override
    public Long getId() {
        return orderId;
    }

    /** Only ever inserted (callers check first), so save() inserts without a select. */
    @Override
    public boolean isNew() {
        return true;
    }
}
