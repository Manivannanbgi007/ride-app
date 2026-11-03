package com.fooddelivery.aimcpclient.dto.response;

/** Whether the assistant can answer right now. */
public record StatusResponse(boolean available, String model, String message) {

    public static StatusResponse unavailable() {
        return new StatusResponse(false, null, "The assistant is not available right now.");
    }
}
