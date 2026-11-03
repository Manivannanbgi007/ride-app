package com.fooddelivery.reviewservice.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The rating of one restaurant or dish with the number of ratings per star.
 *
 * @param average      one decimal (half up), or null when there are no ratings yet
 * @param distribution number of ratings per star, keys "1" to "5" (zero when none)
 */
public record RatingDetailResponse(long id, BigDecimal average, long count, Map<String, Long> distribution) {
}
