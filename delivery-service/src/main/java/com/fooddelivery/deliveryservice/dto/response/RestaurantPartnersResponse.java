package com.fooddelivery.deliveryservice.dto.response;

import java.time.LocalDate;
import java.util.List;

/** The delivery partners who delivered the most orders of one restaurant in a period (calendar days, both included). */
public record RestaurantPartnersResponse(
        long restaurantId,
        LocalDate from,
        LocalDate to,
        List<Partner> partners
) {

    /**
     * @param name           null when this service has no name for the partner
     * @param averageMinutes from "food ready" to "delivered" (one decimal), null when unknown
     */
    public record Partner(long partnerUserId, String name, long delivered, Double averageMinutes) {
    }
}
