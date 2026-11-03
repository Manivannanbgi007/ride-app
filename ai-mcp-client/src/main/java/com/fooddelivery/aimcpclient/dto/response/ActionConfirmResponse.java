package com.fooddelivery.aimcpclient.dto.response;

import java.math.BigDecimal;

/**
 * Result of the Confirm button (the answer of ai-mcp-server, passed on).
 *
 * @param status  CREATED (an order was placed) or DONE (another change was made); ALREADY_CREATED / ALREADY_DONE
 *                when the button was pressed twice
 * @param order   the order (CREATED / ALREADY_CREATED only, else null)
 * @param message text for the chat
 * @param link    where to see the result
 */
public record ActionConfirmResponse(String status, Order order, String message, Link link) {

    public record Order(String id, String status, BigDecimal total, String currency) {
    }

    public record Link(String label, String path) {
    }
}
