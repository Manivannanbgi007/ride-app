package com.fooddelivery.chatservice.client;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Asks notification-service who is connected to the live updates right now. */
public interface PresenceLookup {

    /**
     * Best effort, never throws: presence is a nicety on the inbox, not something a request may fail for.
     *
     * @return the ids that are online, or empty when the answer is unknown (service down, slow, or an error)
     */
    Optional<Set<Long>> findOnline(Collection<Long> userIds);
}
