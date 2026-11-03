package com.fooddelivery.chatservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** An admin starts (or opens) the conversation with a user. */
public record OpenConversationRequest(

        @NotNull(message = "userId is required")
        @Positive(message = "userId must be a positive number")
        Long userId
) {
}
