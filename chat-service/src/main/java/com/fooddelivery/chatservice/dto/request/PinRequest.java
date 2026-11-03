package com.fooddelivery.chatservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record PinRequest(

        @NotNull(message = "pinned is required")
        Boolean pinned
) {
}
