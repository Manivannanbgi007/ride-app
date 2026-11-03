package com.fooddelivery.userservice.exception;

/**
 * The refresh token is missing, unknown, expired or revoked: the user has to sign in again. Mapped to HTTP 401.
 * The message is the same for every case, so a caller cannot probe which tokens exist.
 */
public class SessionEndedException extends AuthenticationFailedException {

    public static final String MESSAGE = "Your session has ended. Please sign in again.";

    public SessionEndedException() {
        super(MESSAGE);
    }
}
