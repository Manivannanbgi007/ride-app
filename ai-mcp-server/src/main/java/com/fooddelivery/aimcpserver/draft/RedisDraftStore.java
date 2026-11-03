package com.fooddelivery.aimcpserver.draft;

import java.time.Duration;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Drafts as JSON under ai:draft:{userId}. Any failure is reported as a DraftStoreException (the service copes). */
@Component
public class RedisDraftStore implements DraftStore {

    private static final String KEY_PREFIX = "ai:draft:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisDraftStore(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void save(long userId, OrderDraft draft, Duration keepFor) {
        try {
            redis.opsForValue().set(KEY_PREFIX + userId, jsonMapper.writeValueAsString(draft), keepFor);
        } catch (DataAccessException | JacksonException e) {
            throw new DraftStoreException("Could not save the order draft", e);
        }
    }

    @Override
    public Optional<OrderDraft> find(long userId) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + userId);
            return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, OrderDraft.class));
        } catch (DataAccessException | JacksonException e) {
            throw new DraftStoreException("Could not read the order draft", e);
        }
    }

    @Override
    public void delete(long userId) {
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (DataAccessException e) {
            throw new DraftStoreException("Could not delete the order draft", e);
        }
    }
}
