package com.fooddelivery.aimcpserver.action;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The request body of a change: only these fields exist, so a stored action can never send anything else.
 * Every field is optional; the ones left null are not sent.
 *
 * @param reason        cancel, reject, decline and reassign
 * @param available     menu item availability
 * @param partnerUserId reassign: the partner who gets the delivery (null: back to the queue)
 * @param cashCollected delivered, cash on delivery: exactly the amount to collect
 */
public record ActionBody(String reason, Boolean available, Long partnerUserId, BigDecimal cashCollected) {

    public static final ActionBody NONE = new ActionBody(null, null, null, null);

    public static ActionBody reason(String reason) {
        return new ActionBody(reason, null, null, null);
    }

    /** The JSON body to send, or null when there is nothing to send. */
    public Map<String, Object> toRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (reason != null) {
            body.put("reason", reason);
        }
        if (available != null) {
            body.put("available", available);
        }
        if (partnerUserId != null) {
            body.put("partnerUserId", partnerUserId);
        }
        if (cashCollected != null) {
            body.put("cashCollected", cashCollected);
        }
        return body.isEmpty() ? null : body;
    }
}
