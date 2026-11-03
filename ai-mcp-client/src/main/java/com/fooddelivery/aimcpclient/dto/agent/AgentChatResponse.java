package com.fooddelivery.aimcpclient.dto.agent;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Answer of ai-mcp-server. Cards, links and the pending action are opaque here: they are passed to the browser
 * as they are, without being interpreted.
 */
public record AgentChatResponse(String text,
                                JsonNode cards,
                                JsonNode links,
                                JsonNode pendingAction,
                                List<String> toolsUsed,
                                String model,
                                Long durationMs,
                                Integer toolCalls) {
}
