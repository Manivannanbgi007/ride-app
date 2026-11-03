package com.fooddelivery.catalogservice.dto.response;

/** @param republished how many events were written to the outbox (they reach Kafka within a second or two) */
public record RepublishResponse(int republished) {
}
