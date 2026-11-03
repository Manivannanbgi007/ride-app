package com.fooddelivery.aimcpclient.dto.response;

import java.util.List;

import tools.jackson.databind.JsonNode;

/** What the assistant said: text plus opaque cards, links and an optional pending action. */
public record AssistantReply(String text,
                             JsonNode cards,
                             JsonNode links,
                             JsonNode pendingAction,
                             List<String> toolsUsed) {
}
