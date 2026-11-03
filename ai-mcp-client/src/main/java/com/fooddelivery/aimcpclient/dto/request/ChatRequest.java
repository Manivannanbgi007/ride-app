package com.fooddelivery.aimcpclient.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One chat message from the browser.
 *
 * @param conversationId the conversation to continue; null starts a new one
 * @param message        the question (trimmed; 1 to 1000 characters; no control characters except new line and tab)
 */
public record ChatRequest(
        UUID conversationId,
        @NotBlank(message = "The message must not be empty.")
        @Size(max = 1000, message = "The message must be at most 1000 characters.")
        // Control characters (except tab, new line, carriage return) can hide instructions or break logs
        @Pattern(regexp = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*",
                message = "The message must not contain control characters.")
        String message) {

    /** Trims here, so the length rules apply to what is really sent on. */
    public ChatRequest {
        message = message == null ? null : message.strip();
    }
}
