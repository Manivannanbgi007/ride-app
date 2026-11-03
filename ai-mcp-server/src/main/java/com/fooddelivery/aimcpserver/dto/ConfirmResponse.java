package com.fooddelivery.aimcpserver.dto;

import java.math.BigDecimal;

/** The answer to a Confirm button: an order placed (with its order), or another change done (order is null). */
public record ConfirmResponse(Status status, OrderRef order, String message, Link link) {

    /**
     * CREATED / ALREADY_CREATED: an order was placed (ALREADY_: by an earlier press; never a second order).
     * DONE / ALREADY_DONE: another change was made (ALREADY_: by an earlier press; it is never made twice).
     */
    public enum Status {
        CREATED, ALREADY_CREATED, DONE, ALREADY_DONE
    }

    public record OrderRef(long id, String status, BigDecimal total, String currency) {
    }
}
