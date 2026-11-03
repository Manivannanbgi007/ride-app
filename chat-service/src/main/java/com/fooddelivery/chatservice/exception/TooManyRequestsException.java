package com.fooddelivery.chatservice.exception;

/** The caller sends faster than the rate limit allows. Mapped to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
