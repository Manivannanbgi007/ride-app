package com.fooddelivery.aimcpserver.proposal;

import java.time.Duration;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Proposals as JSON under ai:proposal:{id}; the lock is a separate key written with SET NX and an expiry. */
@Component
public class RedisProposalStore implements ProposalStore {

    private static final String KEY_PREFIX = "ai:proposal:";
    private static final String LOCK_SUFFIX = ":lock";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public RedisProposalStore(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void save(Proposal proposal, Duration keepFor) {
        try {
            redis.opsForValue().set(KEY_PREFIX + proposal.id(), jsonMapper.writeValueAsString(proposal), keepFor);
        } catch (DataAccessException | JacksonException e) {
            throw new ProposalStoreException("Could not save the proposal", e);
        }
    }

    @Override
    public Optional<Proposal> find(String id) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + id);
            return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, Proposal.class));
        } catch (DataAccessException | JacksonException e) {
            throw new ProposalStoreException("Could not read the proposal", e);
        }
    }

    @Override
    public boolean tryLock(String id, Duration lockFor) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY_PREFIX + id + LOCK_SUFFIX, "1", lockFor));
        } catch (DataAccessException e) {
            throw new ProposalStoreException("Could not lock the proposal", e);
        }
    }

    @Override
    public void unlock(String id) {
        try {
            redis.delete(KEY_PREFIX + id + LOCK_SUFFIX);
        } catch (DataAccessException e) {
            // ProposalService logs and ignores this: the lock expires by itself
            throw new ProposalStoreException("Could not release the proposal lock", e);
        }
    }
}
