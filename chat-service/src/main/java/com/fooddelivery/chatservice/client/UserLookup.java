package com.fooddelivery.chatservice.client;

import java.util.Optional;

/** Asks user-service about a user. An interface, so tests can replace the remote call. */
public interface UserLookup {

    /**
     * When user-service cannot be reached this throws {@code DependencyUnavailableException} (HTTP 503) instead of
     * guessing a name: the callers save nothing in that case.
     *
     * @return the user, or empty when user-service does not know the id
     */
    Optional<UserInfo> findUser(long userId);
}
