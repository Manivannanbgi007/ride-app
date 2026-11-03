package com.fooddelivery.aimcpserver.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything configurable that is not a Spring AI setting. URLs and the secret come from environment variables
 * (see application.yml).
 *
 * @param serviceToken shared secret every /internal and /mcp call must carry (X-Service-Token)
 * @param timezone     the business time zone: "today" means today here
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String serviceToken,
        ZoneId timezone,
        Gateway gateway,
        Ollama ollama,
        Agent agent,
        Proposal proposal
) {

    /** The API Gateway: the only place the business APIs are called through. */
    public record Gateway(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    /**
     * @param model          must match spring.ai.ollama.chat.options.model (shown by the status endpoint)
     * @param requestTimeout how long one model call may take (a CPU-only model is slow)
     * @param statusTimeout  timeout of the availability probe
     */
    public record Ollama(String baseUrl, String model, Duration requestTimeout, Duration statusTimeout) {
    }

    /**
     * @param maxToolCalls        tool rounds per question; after that the model must answer with what it has
     * @param maxDuration         whole question, including the model's thinking time
     * @param toolTimeout         one tool call (prepare_order has its own, longer one)
     * @param maxHistoryChars     history sent to the model, oldest turns dropped first (the context is small)
     * @param maxToolResultChars  a tool result longer than this is cut
     */
    public record Agent(int maxToolCalls, Duration maxDuration, Duration toolTimeout, int maxHistory,
                        int maxHistoryChars, int maxToolResultChars) {
    }

    /** @param ttl how long the customer can confirm; the Redis key lives a little longer to answer "expired" (410) */
    public record Proposal(Duration ttl, Duration keepAfterExpiry, Duration confirmedKeep, Duration lockTtl) {
    }
}
