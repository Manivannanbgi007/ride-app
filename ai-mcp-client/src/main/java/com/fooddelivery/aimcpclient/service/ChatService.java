package com.fooddelivery.aimcpclient.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fooddelivery.aimcpclient.client.AgentServerClient;
import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatRequest;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatResponse;
import com.fooddelivery.aimcpclient.dto.request.ChatRequest;
import com.fooddelivery.aimcpclient.dto.response.AssistantReply;
import com.fooddelivery.aimcpclient.dto.response.ChatResponse;
import com.fooddelivery.aimcpclient.dto.response.StoredMessage;
import com.fooddelivery.aimcpclient.exception.AgentCallException;
import com.fooddelivery.aimcpclient.exception.TooManyRequestsException;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One chat message: limits, history, the agent server, and storing the exchange.
 * Message texts are never logged at INFO (they may contain personal data): only ids and lengths.
 */
@Slf4j
@Service
public class ChatService {

    static final String ROLE_USER = "USER";
    static final String ROLE_ASSISTANT = "ASSISTANT";

    private final ChatRateLimiter rateLimiter;
    private final ConversationStore store;
    private final AgentServerClient agentServer;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final int historyToServer;
    private final Timer chatTimer;

    public ChatService(ChatRateLimiter rateLimiter, ConversationStore store, AgentServerClient agentServer,
                       JsonMapper jsonMapper, MeterRegistry meters, Clock clock, AppProperties properties) {
        this.rateLimiter = rateLimiter;
        this.store = store;
        this.agentServer = agentServer;
        this.jsonMapper = jsonMapper;
        this.meters = meters;
        this.clock = clock;
        this.historyToServer = properties.conversation().historyToServer();
        this.chatTimer = Timer.builder("ai.client.chat.duration")
                .description("Time to answer one chat message, including the agent server")
                .publishPercentileHistogram()
                .register(meters);
    }

    public ChatResponse chat(CallerIdentity caller, ChatRequest request) {
        long startNanos = System.nanoTime();
        String outcome = "error";
        try (ChatRateLimiter.Permit ignored = rateLimiter.acquire(caller.userId())) {
            ChatResponse response = answer(caller, request);
            outcome = "success";
            return response;
        } catch (TooManyRequestsException e) {
            outcome = e.getReason();
            throw e;
        } catch (AgentCallException e) {
            outcome = e.getStatus() == HttpStatus.GATEWAY_TIMEOUT ? "timeout" : "unavailable";
            throw e;
        } finally {
            meters.counter("ai.client.chat.requests", "outcome", outcome).increment();
            chatTimer.record(Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    private ChatResponse answer(CallerIdentity caller, ChatRequest request) {
        long userId = caller.userId();
        UUID conversationId = request.conversationId();
        // An unknown, expired or foreign id (keys include the user id) simply starts a new conversation
        if (conversationId == null || !store.exists(userId, conversationId)) {
            conversationId = UUID.randomUUID();
        }
        List<AgentChatRequest.HistoryEntry> history = store.lastMessages(userId, conversationId, historyToServer).stream()
                .map(message -> new AgentChatRequest.HistoryEntry(message.role(), message.text()))
                .toList();

        AgentChatResponse agentReply = agentServer.chat(caller, new AgentChatRequest(request.message(), history));

        Instant now = clock.instant();
        String text = agentReply.text() == null ? "" : agentReply.text();
        AssistantReply reply = new AssistantReply(text, arrayOrEmpty(agentReply.cards()),
                arrayOrEmpty(agentReply.links()), nullIfJsonNull(agentReply.pendingAction()),
                agentReply.toolsUsed() == null ? List.of() : agentReply.toolsUsed());
        StoredMessage userMessage = new StoredMessage(newId(), ROLE_USER, request.message(), null, null, null, now);
        StoredMessage assistantMessage = new StoredMessage(newId(), ROLE_ASSISTANT, text, reply.cards(), reply.links(),
                reply.pendingAction(), now);
        store.append(userId, conversationId, List.of(userMessage, assistantMessage));

        log.info("chat conversation={} messageChars={} replyChars={} toolsUsed={} toolCalls={} agentMs={}",
                conversationId, request.message().length(), text.length(), reply.toolsUsed().size(),
                agentReply.toolCalls(), agentReply.durationMs());
        long durationMs = agentReply.durationMs() == null ? 0 : agentReply.durationMs();
        return new ChatResponse(conversationId, assistantMessage.id(), reply, agentReply.model(), durationMs);
    }

    private JsonNode arrayOrEmpty(JsonNode node) {
        return node == null || node.isNull() ? jsonMapper.createArrayNode() : node;
    }

    private static JsonNode nullIfJsonNull(JsonNode node) {
        return node == null || node.isNull() ? null : node;
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }
}
