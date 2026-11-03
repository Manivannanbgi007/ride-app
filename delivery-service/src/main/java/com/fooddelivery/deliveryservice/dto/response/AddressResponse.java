package com.fooddelivery.deliveryservice.dto.response;

import java.math.BigDecimal;

public record AddressResponse(String line1, String line2, String city, String state, String postalCode,
                              String country, BigDecimal latitude, BigDecimal longitude) {
}
