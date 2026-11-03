package com.fooddelivery.promotionservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A coupon customers can use today (the "Offers" page).
 *
 * @param summary      the discount in words ("100.00 off", "20% off, up to 150.00")
 * @param restaurantId the only restaurant it works at, or null for every restaurant
 */
public record OfferResponse(
        String code,
        String description,
        String summary,
        BigDecimal minOrderAmount,
        Long restaurantId,
        LocalDate validUntil
) {
}
