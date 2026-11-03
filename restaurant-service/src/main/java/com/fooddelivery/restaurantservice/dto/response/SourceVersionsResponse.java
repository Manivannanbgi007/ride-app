package com.fooddelivery.restaurantservice.dto.response;

import java.util.List;

/**
 * One page of "which restaurants exist, and at which version": what search-service compares its index with
 * (internal API).
 *
 * @param nextAfterId ask for the next page with this value as afterId; null on the last page
 */
public record SourceVersionsResponse(List<Entry> entries, Long nextAfterId) {

    public record Entry(long id, long version) {
    }
}
