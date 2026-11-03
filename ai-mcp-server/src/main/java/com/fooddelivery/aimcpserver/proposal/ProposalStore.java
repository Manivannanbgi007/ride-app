package com.fooddelivery.aimcpserver.proposal;

import java.time.Duration;
import java.util.Optional;

/**
 * Where proposals live (Redis in production). The lock is what makes confirm safe: only one caller at a time may
 * change a proposal or call the order API for it.
 */
public interface ProposalStore {

    void save(Proposal proposal, Duration keepFor);

    Optional<Proposal> find(String id);

    /** Atomic "set if absent" with an expiry; true when this caller now holds the lock. */
    boolean tryLock(String id, Duration lockFor);

    void unlock(String id);
}
