package com.fooddelivery.aimcpclient.exception;

/** The request is malformed: 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
