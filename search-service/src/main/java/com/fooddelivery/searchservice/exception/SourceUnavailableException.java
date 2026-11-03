package com.fooddelivery.searchservice.exception;

/** The service that owns the data (restaurant-service, catalog-service) did not answer: nothing was changed. */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable: " + cause.getMessage(), cause);
    }
}
