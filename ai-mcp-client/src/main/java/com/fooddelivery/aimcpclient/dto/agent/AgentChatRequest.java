package com.fooddelivery.aimcpclient.dto.agent;

import java.util.List;

/** Body of POST /internal/agent/chat (texts only: no cards in the history). */
public record AgentChatRequest(String message, List<HistoryEntry> history) {

    /** @param role USER or ASSISTANT */
    public record HistoryEntry(String role, String text) {
    }
}
