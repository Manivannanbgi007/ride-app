package com.fooddelivery.searchservice.dto.response;

import java.util.List;
import java.util.Map;

/**
 * One page of search results.
 *
 * @param totalHits how many results match in total (all pages)
 * @param facets    for each filter, the values found in all matching results and how often, e.g.
 *                  {"cuisines": [{"value": "Biryani", "count": 12}]}: lets a client show filter chips with counts
 */
public record SearchResults<T>(
        String query,
        long totalHits,
        int page,
        int size,
        int totalPages,
        List<T> results,
        Map<String, List<FacetValue>> facets
) {

    public record FacetValue(String value, long count) {
    }
}
