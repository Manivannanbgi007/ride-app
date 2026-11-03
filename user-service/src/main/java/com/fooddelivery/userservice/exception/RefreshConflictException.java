package com.fooddelivery.userservice.exception;

/**
 * The refresh token was exchanged a moment ago by another request of the same browser (two tabs refreshing at once).
 * The session is fine: the browser already holds the new cookie, so the client simply tries again. Mapped to HTTP 409.
 */
public class RefreshConflictException extends ConflictException {

    public RefreshConflictException() {
        super("The session was refreshed by another request a moment ago. Try again.");
    }
}
