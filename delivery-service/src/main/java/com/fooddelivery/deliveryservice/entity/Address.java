package com.fooddelivery.deliveryservice.entity;

import java.math.BigDecimal;

import jakarta.persistence.Embeddable;

/**
 * A copied address (pickup at the restaurant or drop-off at the customer). Column names are set where it is
 * used, with {@code @AttributeOverride}, because a delivery stores two of them.
 */
@Embeddable
public record Address(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
