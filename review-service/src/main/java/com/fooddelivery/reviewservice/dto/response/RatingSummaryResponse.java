package com.fooddelivery.reviewservice.dto.response;

import java.math.BigDecimal;

/**
 * The rating of one restaurant or dish, as shown on a card.
 *
 * @param id      the restaurant or menu item id
 * @param average one decimal (half up)
 * @param count   number of ratings
 */
public record RatingSummaryResponse(long id, BigDecimal average, long count) {
}
