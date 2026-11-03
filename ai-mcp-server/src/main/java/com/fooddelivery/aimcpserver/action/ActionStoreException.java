package com.fooddelivery.aimcpserver.action;

/** Redis could not be reached or returned something unreadable. */
public class ActionStoreException extends RuntimeException {

    public ActionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
