package com.fooddelivery.catalogservice.exception;

/** The request parameters are invalid (for example an unsupported sort field). Mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
