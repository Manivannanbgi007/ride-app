package com.fooddelivery.deliveryservice.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The delivery of one order. Customer, restaurant and addresses are copies taken when the delivery was
 * created; they can be missing (null) for a short time when the events that carry them are late
 * (see {@link #hasAllDetails()}). Status changes go through DeliveryWorkflow only.
 */
@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id")
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
    @AttributeOverride(name = "line1", column = @Column(name = "pickup_line1"))
    @AttributeOverride(name = "line2", column = @Column(name = "pickup_line2"))
    @AttributeOverride(name = "city", column = @Column(name = "pickup_city"))
    @AttributeOverride(name = "state", column = @Column(name = "pickup_state"))
    @AttributeOverride(name = "postalCode", column = @Column(name = "pickup_postal_code"))
    @AttributeOverride(name = "country", column = @Column(name = "pickup_country"))
    @AttributeOverride(name = "latitude", column = @Column(name = "pickup_latitude", precision = 9, scale = 6))
    @AttributeOverride(name = "longitude", column = @Column(name = "pickup_longitude", precision = 9, scale = 6))
    private Address pickupAddress;

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

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "cash_to_collect", precision = 10, scale = 2)
    private BigDecimal cashToCollect;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "partner_user_id")
    private Long partnerUserId;

    @Column(name = "partner_name", length = 150)
    private String partnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    @Column(name = "cash_collected", precision = 10, scale = 2)
    private BigDecimal cashCollected;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "ready_at", nullable = false)
    private Instant readyAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** Customer, drop-off and pickup are known: the delivery can be offered to a partner. */
    public boolean hasAllDetails() {
        return hasOrderDetails() && pickupAddress != null;
    }

    public boolean hasOrderDetails() {
        return customerId != null && dropoffAddress != null && paymentMethod != null;
    }

    /** Cash on delivery: the partner collects this amount at the door. */
    public boolean collectsCash() {
        return cashToCollect != null && cashToCollect.signum() > 0;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
