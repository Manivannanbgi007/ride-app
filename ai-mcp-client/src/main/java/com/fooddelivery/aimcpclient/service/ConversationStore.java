package com.fooddelivery.aimcpclient.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.dto.response.StoredMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conversations in Redis, so every instance of this service sees the same history.
 * <p>
 * Layout: {@code ai:conv:{userId}:{conversationId}} is a list of JSON messages (oldest first), and
 * {@code ai:convs:{userId}} is a sorted set of the user's conversation ids by last use (to keep at most N).
 * The user id is part of the key, so one user can never read another user's conversation, even with its id.
 * <p>
 * Redis is not required for an answer: when it fails, reads return "nothing stored" and writes are skipped
 * (one warning when it goes down, one info when it is back); the chat then simply has no history.
 */
@Slf4j
@Component
public class ConversationStore {

    private static final String CONVERSATION_KEY = "ai:conv:%d:%s";
    private static final String INDEX_KEY = "ai:convs:%d";
    private static final String TRUNCATION_MARK = "...";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final AppProperties.Conversation limits;
    private final AtomicBoolean redisDown = new AtomicBoolean(false);

    public ConversationStore(StringRedisTemplate redis, JsonMapper jsonMapper, AppProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.limits = properties.conversation();
    }

    public boolean exists(long userId, UUID conversationId) {
        return guarded(() -> Boolean.TRUE.equals(redis.hasKey(key(userId, conversationId))), false);
    }

    /** All stored messages, oldest first; also keeps the conversation alive. */
    public List<StoredMessage> load(long userId, UUID conversationId) {
        return range(userId, conversationId, 0, -1);
    }

    /** The newest {@code count} messages, oldest first. */
    public List<StoredMessage> lastMessages(long userId, UUID conversationId, int count) {
        return range(userId, conversationId, -count, -1);
    }

    /** Adds messages at the end; drops the oldest beyond the limits and renews the lifetime. */
    public void append(long userId, UUID conversationId, List<StoredMessage> messages) {
        List<String> json = messages.stream().map(this::serialize).toList();
        guarded(() -> {
            String key = key(userId, conversationId);
            redis.opsForList().rightPushAll(key, json);
            redis.opsForList().trim(key, -limits.maxMessages(), -1);
            redis.expire(key, limits.ttl());
            registerConversation(userId, conversationId);
            return null;
        }, null);
    }

    /** "New chat": removes the conversation. */
    public void delete(long userId, UUID conversationId) {
        guarded(() -> {
            redis.delete(key(userId, conversationId));
            redis.opsForZSet().remove(indexKey(userId), conversationId.toString());
            return null;
        }, null);
    }

    /**
     * Removes the pending action {@code actionId} from the stored message that carries it, so a restored history can
     * no longer show a Confirm button for it. The action is opaque here: it is found by its id inside the stored JSON.
     *
     * @return the conversation that held it, if any of the caller's conversations did
     */
    public Optional<UUID> clearPendingAction(long userId, String actionId) {
        return guarded(() -> clearPendingActionInRedis(userId, actionId), Optional.empty());
    }

    private Optional<UUID> clearPendingActionInRedis(long userId, String actionId) {
        String quotedId = "\"" + actionId + "\"";
        Set<String> conversationIds = redis.opsForZSet().range(indexKey(userId), 0, -1);
        if (conversationIds == null) {
            return Optional.empty();
        }
        for (String id : conversationIds) {
            String key = CONVERSATION_KEY.formatted(userId, id);
            List<String> raw = redis.opsForList().range(key, 0, -1);
            if (raw == null) {
                continue;
            }
            boolean found = false;
            for (int i = 0; i < raw.size(); i++) {
                StoredMessage message = parse(raw.get(i));
                if (message != null && message.pendingAction() != null
                        && message.pendingAction().toString().contains(quotedId)) {
                    redis.opsForList().set(key, i, serialize(message.withoutPendingAction()));
                    found = true;
                }
            }
            if (found) {
                return Optional.of(UUID.fromString(id));
            }
        }
        return Optional.empty();
    }

    private List<StoredMessage> range(long userId, UUID conversationId, long from, long to) {
        return guarded(() -> {
            String key = key(userId, conversationId);
            List<String> raw = redis.opsForList().range(key, from, to);
            if (raw == null || raw.isEmpty()) {
                return List.<StoredMessage>of();
            }
            redis.expire(key, limits.ttl());
            redis.expire(indexKey(userId), limits.ttl());
            List<StoredMessage> messages = new ArrayList<>(raw.size());
            for (String json : raw) {
                StoredMessage message = parse(json);
                if (message != null) {
                    messages.add(message);
                }
            }
            return messages;
        }, List.<StoredMessage>of());
    }

    /** Keeps the per-user index current and deletes the oldest conversations beyond the limit. */
    private void registerConversation(long userId, UUID conversationId) {
        String index = indexKey(userId);
        redis.opsForZSet().add(index, conversationId.toString(), System.currentTimeMillis());
        redis.expire(index, limits.ttl());
        Long size = redis.opsForZSet().zCard(index);
        if (size != null && size > limits.maxConversations()) {
            Set<String> oldest = redis.opsForZSet().range(index, 0, size - limits.maxConversations() - 1);
            if (oldest != null && !oldest.isEmpty()) {
                oldest.forEach(id -> redis.delete(CONVERSATION_KEY.formatted(userId, id)));
                redis.opsForZSet().remove(index, oldest.toArray());
            }
        }
    }

    /** Caps what one message may cost in Redis: long texts are cut, huge cards are left out. */
    private String serialize(StoredMessage message) {
        StoredMessage capped = message;
        if (message.text() != null && message.text().length() > limits.maxTextChars()) {
            capped = new StoredMessage(message.id(), message.role(),
                    message.text().substring(0, limits.maxTextChars() - TRUNCATION_MARK.length()) + TRUNCATION_MARK,
                    message.cards(), message.links(), message.pendingAction(), message.at());
        }
        String json = jsonMapper.writeValueAsString(capped);
        if (json.length() > limits.maxStoredBytes() && capped.cards() != null) {
            json = jsonMapper.writeValueAsString(capped.withoutCards());
        }
        return json;
    }

    private StoredMessage parse(String json) {
        try {
            StoredMessage message = jsonMapper.readValue(json, StoredMessage.class);
            // A JSON null is read as a NullNode: use a plain null so "no pending action" is one thing
            return new StoredMessage(message.id(), message.role(), message.text(), nullIfJsonNull(message.cards()),
                    nullIfJsonNull(message.links()), nullIfJsonNull(message.pendingAction()), message.at());
        } catch (JacksonException e) {
            log.warn("Skipping an unreadable stored message: {}", e.getOriginalMessage());
            return null;
        }
    }

    private static JsonNode nullIfJsonNull(JsonNode node) {
        return node == null || node.isNull() ? null : node;
    }

    private <T> T guarded(Supplier<T> operation, T fallback) {
        try {
            T result = operation.get();
            if (redisDown.compareAndSet(true, false)) {
                log.info("Redis is available again: conversations are stored again");
            }
            return result;
        } catch (DataAccessException e) {
            if (redisDown.compareAndSet(false, true)) {
                log.warn("Redis is not available: answering without conversation history ({})",
                        e.getClass().getSimpleName());
            }
            return fallback;
        }
    }

    private static String key(long userId, UUID conversationId) {
        return CONVERSATION_KEY.formatted(userId, conversationId);
    }

    private static String indexKey(long userId) {
        return INDEX_KEY.formatted(userId);
    }
}
