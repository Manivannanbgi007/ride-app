package com.fooddelivery.userservice.repository;

import java.util.Locale;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable query conditions for the admin user search.
 * Each method returns one condition; the service combines only the ones that were requested.
 */
public final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    /** Matches name, email or phone containing the search text (case-insensitive). */
    public static Specification<User> matchesSearchText(String searchText) {
        String pattern = "%" + escapeLike(searchText.trim().toLowerCase(Locale.ROOT)) + "%";

        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                cb.like(root.get("phone"), pattern, LIKE_ESCAPE));
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    // "%" and "_" typed by the admin must be searched literally, not as wildcards.
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
