package com.fooddelivery.orderservice.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Copy of the customer's address at ordering time (editing the address book later does not move the order). */
@Embeddable
public record DeliveryAddress(

        @Column(name = "delivery_line1", nullable = false)
        String line1,

        @Column(name = "delivery_line2")
        String line2,

        @Column(name = "delivery_city", nullable = false, length = 100)
        String city,

        @Column(name = "delivery_state", nullable = false, length = 100)
        String state,

        @Column(name = "delivery_postal_code", nullable = false, length = 20)
        String postalCode,

        @Column(name = "delivery_country", nullable = false, length = 100)
        String country,

        @Column(name = "delivery_latitude", precision = 9, scale = 6)
        BigDecimal latitude,

        @Column(name = "delivery_longitude", precision = 9, scale = 6)
        BigDecimal longitude
) {
}
