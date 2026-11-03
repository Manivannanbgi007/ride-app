package com.fooddelivery.aimcpserver.dto;

import java.util.List;

public record ChatResponse(
        String text,
        List<Card> cards,
        List<Link> links,
        PendingAction pendingAction,
        List<String> toolsUsed,
        String model,
        long durationMs,
        int toolCalls
) {
}
