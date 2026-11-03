package com.fooddelivery.reviewservice.repository;

import java.time.Instant;

import com.fooddelivery.reviewservice.entity.Review;
import com.fooddelivery.reviewservice.entity.ReviewTag;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks of the admin review list. */
public final class ReviewSpecifications {

    private ReviewSpecifications() {
    }

    public static Specification<Review> forRestaurant(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
    }

    public static Specification<Review> hasRating(int rating) {
        return (root, query, cb) -> cb.equal(root.get("rating"), rating);
    }

    /** A tag is unique within a review, so joining the tags cannot repeat a review. */
    public static Specification<Review> hasTag(ReviewTag tag) {
        return (root, query, cb) -> cb.equal(root.join("tags"), tag);
    }

    /** A dish is rated at most once per review (unique review + menu item), so joining the dishes cannot repeat a review. */
    public static Specification<Review> ratesDish(Long menuItemId) {
        return (root, query, cb) -> cb.equal(root.join("dishes").get("menuItemId"), menuItemId);
    }

    public static Specification<Review> createdFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Review> createdBefore(Instant before) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), before);
    }
}
