package com.fooddelivery.reviewservice.dto.response;

import java.time.Instant;

/** The review shown under a featured dish. {@code rating} is the overall rating of that review. */
public record TopReviewResponse(Long reviewId, String customerName, String comment, int rating, Instant createdAt) {
}
