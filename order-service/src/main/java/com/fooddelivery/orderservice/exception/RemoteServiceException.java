package com.fooddelivery.orderservice.exception;

/** Another microservice could not be reached or failed. Mapped to HTTP 503 when not handled. */
public class RemoteServiceException extends RuntimeException {

    public RemoteServiceException(String serviceName, Throwable cause) {
        super(serviceName + " is unavailable: " + cause.getMessage(), cause);
    }
}
