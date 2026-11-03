package com.fooddelivery.restaurantservice.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Address value object, stored in columns of the restaurants table. Replaced as a whole on update. */
@Embeddable
public record RestaurantAddress(

        @Column(name = "address_line1", nullable = false)
        String line1,

        @Column(name = "address_line2")
        String line2,

        @Column(name = "city", nullable = false, length = 100)
        String city,

        @Column(name = "state", nullable = false, length = 100)
        String state,

        @Column(name = "postal_code", nullable = false, length = 20)
        String postalCode,

        @Column(name = "country", nullable = false, length = 100)
        String country,

        @Column(name = "latitude", precision = 9, scale = 6)
        BigDecimal latitude,

        @Column(name = "longitude", precision = 9, scale = 6)
        BigDecimal longitude
) {
}
