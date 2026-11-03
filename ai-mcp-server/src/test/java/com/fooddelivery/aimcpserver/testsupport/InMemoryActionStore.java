package com.fooddelivery.aimcpserver.testsupport;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fooddelivery.aimcpserver.action.ActionRequest;
import com.fooddelivery.aimcpserver.action.ActionStore;

/** A Redis stand-in for MOCKED tests; tryLock has the same set-if-absent meaning as Redis SET NX. */
public final class InMemoryActionStore implements ActionStore {

    private final Map<String, ActionRequest> actions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> locks = new ConcurrentHashMap<>();

    @Override
    public void save(ActionRequest action, Duration keepFor) {
        actions.put(action.id(), action);
    }

    @Override
    public Optional<ActionRequest> find(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    @Override
    public boolean tryLock(String id, Duration lockFor) {
        return locks.putIfAbsent(id, Boolean.TRUE) == null;
    }

    @Override
    public void unlock(String id) {
        locks.remove(id);
    }

    public int size() {
        return actions.size();
    }
}
