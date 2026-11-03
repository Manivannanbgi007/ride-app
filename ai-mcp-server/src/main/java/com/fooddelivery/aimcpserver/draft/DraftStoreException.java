package com.fooddelivery.aimcpserver.draft;

/** The draft store (Redis) could not be read or written. The service copes: a draft is only a convenience. */
public class DraftStoreException extends RuntimeException {

    public DraftStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
