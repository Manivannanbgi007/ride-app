package com.fooddelivery.aimcpserver.draft;

import java.time.Duration;
import java.util.Optional;

/** Where order drafts live (Redis in production): one draft per user, which expires by itself. */
public interface DraftStore {

    void save(long userId, OrderDraft draft, Duration keepFor);

    Optional<OrderDraft> find(long userId);

    void delete(long userId);
}
