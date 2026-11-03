package com.fooddelivery.notificationservice.web;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fooddelivery.notificationservice.entity.Role;

/**
 * Who is on the other end of a live connection: the user, their role and, for a restaurant owner, the restaurants
 * they own. Fixed when the connection opens (the owner's list only grows, when a restaurant of theirs changes).
 * <p>
 * Built by {@link LiveHandshakeInterceptor} from the connection's verified access token (its subject and role), like
 * {@link CallerInfo} for the REST APIs.
 */
public final class LiveClient {

    private final Long userId;
    private final Role role;
    private final Set<Long> restaurantIds = ConcurrentHashMap.newKeySet();

    public LiveClient(Long userId, Role role, Set<Long> restaurantIds) {
        this.userId = userId;
        this.role = role;
        this.restaurantIds.addAll(restaurantIds);
    }

    public Long userId() {
        return userId;
    }

    public Role role() {
        return role;
    }

    public boolean isPlatformAdmin() {
        return role == Role.PLATFORM_ADMIN;
    }

    public boolean ownsRestaurant(Long restaurantId) {
        return restaurantId != null && restaurantIds.contains(restaurantId);
    }

    /** A restaurant of this owner appeared (a new one was submitted): its orders are theirs to see from now on. */
    public void addRestaurant(Long restaurantId) {
        restaurantIds.add(restaurantId);
    }
}
