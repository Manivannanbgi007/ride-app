package com.fooddelivery.chatservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record MuteRequest(

        @NotNull(message = "muted is required")
        Boolean muted
) {
}
