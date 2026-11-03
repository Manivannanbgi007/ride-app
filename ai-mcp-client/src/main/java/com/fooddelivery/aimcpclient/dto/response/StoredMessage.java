package com.fooddelivery.aimcpclient.dto.response;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * One message as kept in Redis and returned to the browser, so the screen can be restored after a reload.
 *
 * @param role          USER or ASSISTANT
 * @param cards         opaque cards (assistant messages)
 * @param links         opaque links
 * @param pendingAction opaque prepared change (an order or another change); cleared once confirmed or cancelled
 */
public record StoredMessage(String id,
                            String role,
                            String text,
                            JsonNode cards,
                            JsonNode links,
                            JsonNode pendingAction,
                            Instant at) {

    public StoredMessage withoutPendingAction() {
        return new StoredMessage(id, role, text, cards, links, null, at);
    }

    public StoredMessage withoutCards() {
        return new StoredMessage(id, role, text, null, links, pendingAction, at);
    }
}
