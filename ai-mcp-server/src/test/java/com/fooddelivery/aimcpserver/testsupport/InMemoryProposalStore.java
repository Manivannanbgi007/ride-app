package com.fooddelivery.aimcpserver.testsupport;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fooddelivery.aimcpserver.proposal.Proposal;
import com.fooddelivery.aimcpserver.proposal.ProposalStore;

/** A Redis stand-in for MOCKED tests; tryLock has the same set-if-absent meaning as Redis SET NX. */
public final class InMemoryProposalStore implements ProposalStore {

    private final Map<String, Proposal> proposals = new ConcurrentHashMap<>();
    private final Map<String, Boolean> locks = new ConcurrentHashMap<>();

    @Override
    public void save(Proposal proposal, Duration keepFor) {
        proposals.put(proposal.id(), proposal);
    }

    @Override
    public Optional<Proposal> find(String id) {
        return Optional.ofNullable(proposals.get(id));
    }

    @Override
    public boolean tryLock(String id, Duration lockFor) {
        return locks.putIfAbsent(id, Boolean.TRUE) == null;
    }

    @Override
    public void unlock(String id) {
        locks.remove(id);
    }
}
