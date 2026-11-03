package com.fooddelivery.catalogservice.repository;

import java.math.BigDecimal;
import java.util.Locale;

import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.CategoryStatus;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import org.springframework.data.jpa.domain.Specification;

/** Reusable query conditions for the admin lists; services combine only the requested filters. */
public final class CatalogSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private CatalogSpecifications() {
    }

    // ---- Categories ----

    public static Specification<Category> categoryNameContains(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), containsPattern(text), LIKE_ESCAPE);
    }

    public static Specification<Category> categoryHasStatus(CategoryStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    // ---- Menu items ----

    public static Specification<MenuItem> itemNameContains(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), containsPattern(text), LIKE_ESCAPE);
    }

    public static Specification<MenuItem> itemOfRestaurant(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
    }

    public static Specification<MenuItem> itemInCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<MenuItem> itemHasStatus(MenuItemStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<MenuItem> itemIsAvailable(boolean available) {
        return (root, query, cb) -> cb.equal(root.get("available"), available);
    }

    public static Specification<MenuItem> itemPriceAtLeast(BigDecimal minPrice) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<MenuItem> itemPriceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    private static String containsPattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
