package com.fooddelivery.aimcpserver.action;

import java.time.Duration;
import java.util.Optional;

/**
 * Where prepared actions live (Redis in production). The lock lets one confirm at a time call the business API for an
 * action, so a double click or two tabs make the change once.
 */
public interface ActionStore {

    void save(ActionRequest action, Duration keepFor);

    Optional<ActionRequest> find(String id);

    /** Atomic "set if absent" with an expiry; true when this caller now holds the lock. */
    boolean tryLock(String id, Duration lockFor);

    void unlock(String id);
}
