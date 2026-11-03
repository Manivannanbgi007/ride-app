package com.fooddelivery.aimcpserver.draft;

import java.time.Instant;
import java.util.List;

/**
 * An order the customer is still describing: what was understood so far, in the customer's own words. It lives on the
 * server (Redis), not in the model's memory, because a small model forgets earlier turns; the system prompt of the
 * next turn is built from it and prepare_order merges the new details into it. Everything is resolved and checked
 * again at every prepare_order, so a stale draft can never place anything: it only saves the customer from repeating.
 *
 * @param missing what the last prepare_order still needed (the question it asked), for the next prompt
 */
public record OrderDraft(String restaurant, List<Item> items, String address, String paymentMethod, String couponCode,
                         String notes, String missing, Instant savedAt) {

    /** One requested dish as the customer said it. */
    public record Item(String dish, Integer quantity) {
    }

    public OrderDraft {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean isEmpty() {
        return blank(restaurant) && items.isEmpty() && blank(address) && blank(paymentMethod);
    }

    static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
