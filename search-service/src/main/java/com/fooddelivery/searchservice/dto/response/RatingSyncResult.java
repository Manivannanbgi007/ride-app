package com.fooddelivery.searchservice.dto.response;

/**
 * What one rating sync did. "Updated" counts the indexed documents that now carry the rating review-service reported
 * (only the ones whose stored rating differed are actually rewritten); ids that are not indexed are ignored.
 * "Reset" counts the documents that had a rating and no longer have reviews: they are back to "no ratings".
 */
public record RatingSyncResult(long restaurantsUpdated, long foodsUpdated, long restaurantsReset, long foodsReset) {
}
