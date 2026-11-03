package com.fooddelivery.aimcpclient.exception;

import lombok.Getter;

/** The caller is over a limit: 429 with a Retry-After header. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    /** Too many messages in the time window. */
    public static final String REASON_RATE_LIMIT = "rate_limited";
    /** Too many questions being answered at the same time. */
    public static final String REASON_BUSY = "busy";

    private final long retryAfterSeconds;
    /** Short label for metrics (one of the REASON_ constants). */
    private final String reason;

    public TooManyRequestsException(String message, long retryAfterSeconds, String reason) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.reason = reason;
    }
}
