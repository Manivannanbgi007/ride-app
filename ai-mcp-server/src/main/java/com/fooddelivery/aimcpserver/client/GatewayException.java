package com.fooddelivery.aimcpserver.client;

/**
 * A failed call to a business API (through the gateway). The message is written for the model and the user: no
 * hosts, stack traces or raw response bodies.
 */
public class GatewayException extends RuntimeException {

    private final int status;
    private final boolean transientFailure;
    private final boolean timedOut;

    /**
     * @param status           the HTTP status, or 0 when there was no response (timeout, connection refused)
     * @param transientFailure true when trying again later can work (timeouts, 5xx, open circuit)
     * @param timedOut         true when the platform did not answer in time (a retry would only double the wait)
     */
    public GatewayException(int status, boolean transientFailure, boolean timedOut, String message) {
        super(message);
        this.status = status;
        this.transientFailure = transientFailure;
        this.timedOut = timedOut;
    }

    public int getStatus() {
        return status;
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}
