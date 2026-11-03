package com.fooddelivery.adminservice.exception;

/** The requested resource does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
