package com.fooddelivery.aimcpserver.testsupport;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fooddelivery.aimcpserver.draft.DraftStore;
import com.fooddelivery.aimcpserver.draft.OrderDraft;

/** Order drafts in memory for the MOCKED tests (Redis is not needed to test what the service does with them). */
public final class InMemoryDraftStore implements DraftStore {

    private final Map<Long, OrderDraft> drafts = new ConcurrentHashMap<>();
    private final Map<Long, Duration> keptFor = new ConcurrentHashMap<>();

    @Override
    public void save(long userId, OrderDraft draft, Duration keepFor) {
        drafts.put(userId, draft);
        keptFor.put(userId, keepFor);
    }

    @Override
    public Optional<OrderDraft> find(long userId) {
        return Optional.ofNullable(drafts.get(userId));
    }

    @Override
    public void delete(long userId) {
        drafts.remove(userId);
        keptFor.remove(userId);
    }

    /** How long the draft was asked to be kept (what Redis would use as expiry). */
    public Duration keptFor(long userId) {
        return keptFor.get(userId);
    }
}
