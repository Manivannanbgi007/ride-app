package com.fooddelivery.aimcpclient.dto.response;

import java.util.UUID;

/** Answer to one chat message. */
public record ChatResponse(UUID conversationId, String messageId, AssistantReply reply, String model, long durationMs) {
}
