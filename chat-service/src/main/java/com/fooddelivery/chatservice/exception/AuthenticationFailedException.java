package com.fooddelivery.chatservice.exception;

/** The caller could not be identified (bad credentials or missing identity). Mapped to HTTP 401. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
