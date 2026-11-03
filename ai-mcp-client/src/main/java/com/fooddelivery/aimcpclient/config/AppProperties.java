package com.fooddelivery.aimcpclient.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param agentServer  ai-mcp-server: the LLM agent and its tools
 * @param identity     what the caller's access token must still be good for
 * @param conversation how conversations are kept in Redis
 * @param rateLimit    per-user limits
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue AgentServer agentServer,
                            @DefaultValue Identity identity,
                            @DefaultValue Conversation conversation,
                            @DefaultValue RateLimit rateLimit) {

    /**
     * @param baseUrl          where ai-mcp-server listens
     * @param serviceToken     shared secret sent as X-Service-Token (no default in production)
     * @param connectTimeout   connect timeout
     * @param readTimeout      read timeout: long, because a CPU model is slow
     * @param maxResponseBytes largest answer accepted
     * @param breakerFailures  consecutive failures that open the circuit breaker
     * @param breakerOpenFor   how long calls are refused while the breaker is open
     */
    public record AgentServer(String baseUrl,
                              String serviceToken,
                              @DefaultValue("2s") Duration connectTimeout,
                              @DefaultValue("150s") Duration readTimeout,
                              @DefaultValue("262144") int maxResponseBytes,
                              @DefaultValue("5") int breakerFailures,
                              @DefaultValue("20s") Duration breakerOpenFor) {
    }

    /**
     * @param minTokenValidity an access token with less time left is refused (401: the browser renews it and asks
     *                         again). ai-mcp-server calls the platform with the same token for up to 150 s, so it must
     *                         not expire in the middle of an answer.
     */
    public record Identity(@DefaultValue("180s") Duration minTokenValidity) {
    }

    /**
     * @param ttl             lifetime of a conversation, refreshed on every use
     * @param maxMessages     stored messages per conversation (oldest dropped)
     * @param maxConversations conversations per user (oldest dropped)
     * @param historyToServer history entries sent to the agent server
     * @param maxTextChars    stored text length per message
     * @param maxStoredBytes  a stored message above this size is stored without its cards
     */
    public record Conversation(@DefaultValue("2h") Duration ttl,
                               @DefaultValue("40") int maxMessages,
                               @DefaultValue("5") int maxConversations,
                               @DefaultValue("12") int historyToServer,
                               @DefaultValue("4000") int maxTextChars,
                               @DefaultValue("65536") int maxStoredBytes) {
    }

    /**
     * @param chatMessages messages allowed per window
     * @param window       the fixed window
     * @param maxInFlight  chats a user may have running at the same time
     */
    public record RateLimit(@DefaultValue("20") int chatMessages,
                            @DefaultValue("10m") Duration window,
                            @DefaultValue("3") int maxInFlight) {
    }
}
