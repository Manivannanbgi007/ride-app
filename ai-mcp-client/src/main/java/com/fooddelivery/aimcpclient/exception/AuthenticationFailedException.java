package com.fooddelivery.aimcpclient.exception;

/** The caller cannot be identified: 401. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
