package com.fooddelivery.searchservice.dto.response;

import java.util.List;

/**
 * One page of "which ids have ratings, and what is their average", as review-service answers (internal API).
 *
 * @param items       the rated ids of this page, ascending (only ids with at least one review)
 * @param nextAfterId ask for the next page with this as afterId; null on the last page
 */
public record RatingFeed(List<Item> items, Long nextAfterId) {

    /**
     * @param average one decimal, 1.0 to 5.0
     * @param count   how many ratings the average is made of (at least 1)
     */
    public record Item(long id, double average, int count) {
    }
}
