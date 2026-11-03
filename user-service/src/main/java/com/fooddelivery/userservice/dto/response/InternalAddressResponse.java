package com.fooddelivery.userservice.dto.response;

import java.math.BigDecimal;

/** A user's address for other microservices (for example the delivery address copied into an order). */
public record InternalAddressResponse(
        Long id,
        Long userId,
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
