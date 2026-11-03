package com.fooddelivery.paymentservice.dto.response;

import java.time.Instant;
import java.util.List;

/** Admin view: the payment, its refunds, and the events it published (e.g. whether order-service was told). */
public record PaymentDetailsResponse(
        PaymentResponse payment,
        List<RefundResponse> refunds,
        List<PublishedEvent> events
) {

    /**
     * An event of this payment in the transactional outbox.
     *
     * @param publishedAt null while it waits for Kafka; lastError says why the last attempt failed
     */
    public record PublishedEvent(String eventId, String eventType, String topic, String status,
                                 Instant createdAt, Instant publishedAt, int attempts, String lastError) {
    }
}
