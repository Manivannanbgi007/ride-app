package com.fooddelivery.aimcpclient.dto.response;

import java.util.List;
import java.util.UUID;

/** The caller's stored conversation (empty list when there is none). */
public record ConversationResponse(UUID conversationId, List<StoredMessage> messages) {
}
