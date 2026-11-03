package com.fooddelivery.notificationservice.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * "Something changed": a hint that the screen showing this order, delivery or restaurant is out of date. It holds
 * ids and the new status only, nothing about people (no names, addresses or amounts). The application then reads the
 * current data through the normal REST APIs, which check what the user may see, so a live update can never show more
 * than a refresh would.
 *
 * @param id             the platform event's id: the same event never has two ids, so a receiver can skip repeats
 * @param type           ORDER_STATUS_CHANGED, ORDER_CANCELLED, PAYMENT_STATUS_CHANGED, DELIVERY_STATUS_CHANGED or
 *                       RESTAURANT_CHANGED, CHAT_MESSAGE or CHAT_READ
 * @param occurredAt     when it happened
 * @param status         the new status of the order, payment, delivery or restaurant
 * @param previousStatus the status before, when known
 * @param conversationId chat events: the conversation
 * @param messageId      CHAT_MESSAGE: the new message (its text is read over REST)
 * @param fromStaff      CHAT_MESSAGE: true when the support staff wrote it
 * @param readByStaff    CHAT_READ: true when the staff read the participant's messages, false for the reverse
 * @param upToMessageId  CHAT_READ: every message up to this one is read
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveEvent(String id,
                        String type,
                        Instant occurredAt,
                        Long orderId,
                        Long deliveryId,
                        Long paymentId,
                        Long restaurantId,
                        String status,
                        String previousStatus,
                        Long conversationId,
                        Long messageId,
                        Boolean fromStaff,
                        Boolean readByStaff,
                        Long upToMessageId) {

    /** An order, payment, delivery or restaurant change (no chat fields). */
    public LiveEvent(String id, String type, Instant occurredAt, Long orderId, Long deliveryId, Long paymentId,
                     Long restaurantId, String status, String previousStatus) {
        this(id, type, occurredAt, orderId, deliveryId, paymentId, restaurantId, status, previousStatus,
                null, null, null, null, null);
    }
}
