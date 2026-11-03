package com.fooddelivery.aimcpserver.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** history may be longer than the limit: the oldest turns are dropped, not rejected. */
public record ChatRequest(
        @NotBlank @Size(max = 1000) String message,
        @Valid List<ChatMessage> history
) {
}
