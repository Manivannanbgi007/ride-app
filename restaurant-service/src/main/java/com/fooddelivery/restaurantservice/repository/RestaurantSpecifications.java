package com.fooddelivery.restaurantservice.repository;

import java.util.Locale;

import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import org.springframework.data.jpa.domain.Specification;

/** Reusable query conditions; services combine only the filters that were requested. */
public final class RestaurantSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private RestaurantSpecifications() {
    }

    public static Specification<Restaurant> hasStatus(RestaurantStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Case-insensitive exact city match (the column collation is case-insensitive). */
    public static Specification<Restaurant> inCity(String city) {
        return (root, query, cb) -> cb.equal(root.get("address").get("city"), city.trim());
    }

    public static Specification<Restaurant> nameContains(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), containsPattern(text), LIKE_ESCAPE);
    }

    public static Specification<Restaurant> servesCuisine(String cuisine) {
        // cuisines is stored as "North Indian,Chinese": a contains match is enough for now.
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("cuisines").as(String.class)), containsPattern(cuisine), LIKE_ESCAPE);
    }

    /** Admin search: restaurant name, legal business name or city. */
    public static Specification<Restaurant> matchesAdminSearch(String text) {
        String pattern = containsPattern(text);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("legalBusinessName")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("address").get("city")), pattern, LIKE_ESCAPE));
    }

    private static String containsPattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
