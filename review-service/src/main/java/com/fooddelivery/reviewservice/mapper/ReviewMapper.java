package com.fooddelivery.reviewservice.mapper;

import java.util.Comparator;
import java.util.List;

import com.fooddelivery.reviewservice.dto.response.DishRatingResponse;
import com.fooddelivery.reviewservice.dto.response.PublicReviewResponse;
import com.fooddelivery.reviewservice.dto.response.ReviewResponse;
import com.fooddelivery.reviewservice.dto.response.TopReviewResponse;
import com.fooddelivery.reviewservice.entity.Review;
import com.fooddelivery.reviewservice.entity.ReviewDish;
import com.fooddelivery.reviewservice.entity.ReviewTag;
import org.springframework.stereotype.Component;

/** Entity to DTO. Call it inside a transaction: tags and dishes are loaded lazily. */
@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getOrderId(),
                review.getCustomerId(),
                review.getCustomerName(),
                review.getRestaurantId(),
                review.getRestaurantName(),
                review.getRating(),
                review.getComment(),
                sortedTags(review),
                sortedDishes(review),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getVersion());
    }

    /** What any visitor may see: a short form of the name, and neither the customer nor the order. */
    public PublicReviewResponse toPublicResponse(Review review) {
        return new PublicReviewResponse(
                review.getId(),
                ReviewerNames.mask(review.getCustomerName()),
                review.getRating(),
                review.getComment(),
                sortedTags(review),
                sortedDishes(review),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    public TopReviewResponse toTopReview(Review review) {
        return new TopReviewResponse(review.getId(), review.getCustomerName(), review.getComment(),
                review.getRating(), review.getCreatedAt());
    }

    /** A stable order, whatever the database returns */
    private static List<ReviewTag> sortedTags(Review review) {
        return review.getTags().stream().sorted().toList();
    }

    private static List<DishRatingResponse> sortedDishes(Review review) {
        return review.getDishes().stream()
                .sorted(Comparator.comparing(ReviewDish::getMenuItemId))
                .map(dish -> new DishRatingResponse(dish.getMenuItemId(), dish.getDishName(), dish.getRating()))
                .toList();
    }
}
