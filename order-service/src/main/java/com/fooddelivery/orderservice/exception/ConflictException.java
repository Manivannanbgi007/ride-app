package com.fooddelivery.orderservice.exception;

/** The request conflicts with the current state of the resource. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
