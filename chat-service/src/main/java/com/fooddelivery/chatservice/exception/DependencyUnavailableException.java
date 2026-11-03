package com.fooddelivery.chatservice.exception;

/**
 * A service this one needs (user-service) did not answer, so the request cannot be decided. Mapped to HTTP 503.
 * Nothing has been saved when it is thrown: remote calls happen before the database transaction starts.
 */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable: " + cause.getMessage(), cause);
    }
}
