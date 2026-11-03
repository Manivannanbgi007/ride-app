package com.fooddelivery.aimcpserver.proposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.aimcpserver.dto.PendingAction;

/**
 * An order the assistant prepared and the customer may confirm. Stored in Redis, bound to the user who asked for it.
 * Nothing is ordered until Confirm; the id is also the idempotency key of the order, so one proposal is at most one order.
 *
 * @param orderId set once CONFIRMED (also used to answer a repeated confirm with the same order)
 * @param failure why a proposal was cancelled by a permanent failure, else null
 */
public record Proposal(
        String id,
        long userId,
        Status status,
        Instant createdAt,
        Instant expiresAt,
        long restaurantId,
        List<Line> items,
        long addressId,
        String paymentMethod,
        String couponCode,
        String notes,
        PendingAction.Summary summary,
        Long orderId,
        String orderStatus,
        BigDecimal orderTotal,
        String orderCurrency,
        String failure
) {

    public enum Status {
        NEW, CONFIRMED, CANCELLED
    }

    public record Line(long menuItemId, int quantity) {
    }

    public Proposal cancelled(String reason) {
        return new Proposal(id, userId, Status.CANCELLED, createdAt, expiresAt, restaurantId, items, addressId,
                paymentMethod, couponCode, notes, summary, orderId, orderStatus, orderTotal, orderCurrency, reason);
    }

    public Proposal confirmed(long order, String status, BigDecimal total, String currency) {
        return new Proposal(id, userId, Status.CONFIRMED, createdAt, expiresAt, restaurantId, items, addressId,
                paymentMethod, couponCode, notes, summary, order, status, total, currency, null);
    }

    public PendingAction pendingAction() {
        return new PendingAction(id, PendingAction.PLACE_ORDER, summary, null, expiresAt);
    }
}
