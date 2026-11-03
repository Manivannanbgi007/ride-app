package com.fooddelivery.chatservice.dto.response;

import java.time.Instant;

/** {@code readAt}: when the other side read the message, null while unread. */
public record MessageResponse(
        Long id,
        Long conversationId,
        String clientMessageId,
        Long senderUserId,
        String senderName,
        String senderRole,
        boolean fromStaff,
        String body,
        Instant createdAt,
        Instant readAt
) {
}
