package com.fooddelivery.aimcpserver.action;

import java.time.Duration;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Actions as JSON under ai:action:{id}; the lock is a separate key written with SET NX and an expiry. */
@Component
public class RedisActionStore implements ActionStore {

    private static final String KEY_PREFIX = "ai:action:";
    private static final String LOCK_SUFFIX = ":lock";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisActionStore(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void save(ActionRequest action, Duration keepFor) {
        try {
            redis.opsForValue().set(KEY_PREFIX + action.id(), jsonMapper.writeValueAsString(action), keepFor);
        } catch (DataAccessException | JacksonException e) {
            throw new ActionStoreException("Could not save the action", e);
        }
    }

    @Override
    public Optional<ActionRequest> find(String id) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + id);
            return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, ActionRequest.class));
        } catch (DataAccessException | JacksonException e) {
            throw new ActionStoreException("Could not read the action", e);
        }
    }

    @Override
    public boolean tryLock(String id, Duration lockFor) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY_PREFIX + id + LOCK_SUFFIX, "1", lockFor));
        } catch (DataAccessException e) {
            throw new ActionStoreException("Could not lock the action", e);
        }
    }

    @Override
    public void unlock(String id) {
        try {
            redis.delete(KEY_PREFIX + id + LOCK_SUFFIX);
        } catch (DataAccessException e) {
            // ActionService logs and ignores this: the lock expires by itself
            throw new ActionStoreException("Could not release the action lock", e);
        }
    }
}
