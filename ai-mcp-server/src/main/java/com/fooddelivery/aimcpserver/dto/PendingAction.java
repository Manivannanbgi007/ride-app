package com.fooddelivery.aimcpserver.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A change the user can confirm or cancel with a button: nothing happens until the Confirm button is used.
 * An order (kind PLACE_ORDER) carries its {@link Summary}; every other kind (cancel an order, accept an order, mark a
 * delivery picked up...) carries {@link Details}: a title, what will change, and the label of the Confirm button.
 */
public record PendingAction(String actionId, String kind, Summary summary, Details details, Instant expiresAt) {

    public static final String PLACE_ORDER = "PLACE_ORDER";

    /** Any amount is null when it is not known (for example when the customer's cart is not empty). */
    public record Summary(
            String restaurantName,
            List<Line> items,
            String deliveryAddress,
            String paymentMethod,
            BigDecimal itemsTotal,
            BigDecimal discount,
            BigDecimal deliveryFee,
            BigDecimal tax,
            BigDecimal total,
            String currency,
            String notes,
            List<String> warnings
    ) {
    }

    public record Line(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    /**
     * What a change will do, as the user checks it before confirming: "Accept order #42" with its rows (customer,
     * total, status now and after), a note (for example "the amount paid online is refunded automatically") and the
     * Confirm button's label.
     */
    public record Details(String title, List<Row> rows, String note, String confirmLabel) {
    }

    public record Row(String label, String value) {
    }
}
