package com.fooddelivery.userservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.fooddelivery.userservice.entity.AddressType;

public record AddressResponse(
        Long id,
        AddressType type,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt
) {
}
