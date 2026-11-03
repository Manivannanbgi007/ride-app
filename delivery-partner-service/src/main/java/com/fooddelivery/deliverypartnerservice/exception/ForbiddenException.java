package com.fooddelivery.deliverypartnerservice.exception;

/** The caller is known but is not allowed to do this. Mapped to HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
