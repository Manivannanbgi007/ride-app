package com.fooddelivery.chatservice.dto.request;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A chat message: plain text only. The text is trimmed before it is validated, so "  hi  " is 2 characters and
 * a message of spaces is blank. The client invents {@code clientMessageId} (a UUID) once per message and sends the
 * same one again when it retries, so a lost response never produces a duplicate.
 */
public record SendMessageRequest(

        @NotBlank(message = "body must not be blank")
        @Size(max = 2000, message = "body must be at most 2000 characters")
        String body,

        @NotBlank(message = "clientMessageId is required")
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "clientMessageId must be a UUID")
        String clientMessageId
) {

    public SendMessageRequest {
        body = body == null ? null : body.strip();
        clientMessageId = clientMessageId == null ? null : clientMessageId.strip().toLowerCase(Locale.ROOT);
    }
}
