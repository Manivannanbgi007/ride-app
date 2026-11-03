package com.fooddelivery.aimcpclient.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A call to ai-mcp-server failed. The message is already safe to show to the user (no URLs, no internals);
 * the technical reason is logged where the exception is created.
 */
@Getter
public class AgentCallException extends RuntimeException {

    private final HttpStatus status;

    public AgentCallException(HttpStatus status, String userMessage) {
        super(userMessage);
        this.status = status;
    }
}
