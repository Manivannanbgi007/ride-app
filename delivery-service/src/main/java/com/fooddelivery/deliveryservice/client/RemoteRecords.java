package com.fooddelivery.deliveryservice.client;

import java.math.BigDecimal;

import com.fooddelivery.deliveryservice.entity.Address;

/** Shapes shared by the responses of other services. */
public final class RemoteRecords {

    private RemoteRecords() {
    }

    /** An address as restaurant-service and order-service return it. */
    public record RemoteAddress(String line1, String line2, String city, String state, String postalCode,
                                String country, BigDecimal latitude, BigDecimal longitude) {

        public Address toAddress() {
            return new Address(line1, line2, city, state, postalCode, country, latitude, longitude);
        }
    }
}
