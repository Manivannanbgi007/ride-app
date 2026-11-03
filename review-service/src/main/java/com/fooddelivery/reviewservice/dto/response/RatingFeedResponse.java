package com.fooddelivery.reviewservice.dto.response;

import java.util.List;

/**
 * One page of "which restaurants (or dishes) have ratings, and which ones": what search-service reads (internal API).
 *
 * @param nextAfterId ask for the next page with this value as afterId; null on the last page
 */
public record RatingFeedResponse(List<RatingSummaryResponse> items, Long nextAfterId) {
}
