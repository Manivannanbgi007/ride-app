package com.fooddelivery.aimcpclient.exception;

/** The caller is known but may not use the assistant: 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
