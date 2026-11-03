package com.fooddelivery.aimcpserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One earlier turn, kept by ai-mcp-client. */
public record ChatMessage(
        @NotNull Speaker role,
        @NotBlank @Size(max = 2000) String text
) {

    public enum Speaker {
        USER, ASSISTANT
    }
}
