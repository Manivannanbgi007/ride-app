package com.fooddelivery.reviewservice.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param averageRating one decimal, or null when there are no reviews
 * @param distribution  number of reviews per star, keys "1" to "5" (zero when none)
 */
public record ReviewSummaryResponse(long count, BigDecimal averageRating, Map<String, Long> distribution) {
}
