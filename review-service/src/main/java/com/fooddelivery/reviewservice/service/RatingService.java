package com.fooddelivery.reviewservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fooddelivery.reviewservice.dto.response.PageResponse;
import com.fooddelivery.reviewservice.dto.response.PublicReviewResponse;
import com.fooddelivery.reviewservice.dto.response.RatingDetailResponse;
import com.fooddelivery.reviewservice.dto.response.RatingFeedResponse;
import com.fooddelivery.reviewservice.dto.response.RatingSummaryResponse;
import com.fooddelivery.reviewservice.mapper.ReviewMapper;
import com.fooddelivery.reviewservice.repository.RatingAggregate;
import com.fooddelivery.reviewservice.repository.RatingCount;
import com.fooddelivery.reviewservice.repository.RatingRepository;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import com.fooddelivery.reviewservice.repository.ReviewSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public side: average rating and number of ratings of restaurants and dishes, and the reviews of a restaurant,
 * for visitors who are not signed in; and the same numbers, page by page, for search-service. Read-only.
 * <p>
 * Everything is counted in the database from the actual rows on every call (nothing is cached), so an edited or
 * deleted review shows at once. Restaurants are rated by the overall rating of their reviews, dishes by the
 * ratings given to the dish.
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;

    // ------------------------------------------------------------------ for the storefront

    /** One grouped query for all ids; an id without reviews is simply not in the answer. */
    @Transactional(readOnly = true)
    public List<RatingSummaryResponse> restaurantRatings(Set<Long> restaurantIds) {
        return summaries(ratingRepository.averageByRestaurant(restaurantIds));
    }

    @Transactional(readOnly = true)
    public List<RatingSummaryResponse> menuItemRatings(Set<Long> menuItemIds) {
        return summaries(ratingRepository.averageByMenuItem(menuItemIds));
    }

    /** No reviews yet is not an error: count 0, no average and every star at zero. */
    @Transactional(readOnly = true)
    public RatingDetailResponse restaurantRating(long restaurantId) {
        return detail(restaurantId, ratingRepository.averageByRestaurant(Set.of(restaurantId)),
                ratingRepository.countRestaurantStars(restaurantId));
    }

    @Transactional(readOnly = true)
    public RatingDetailResponse menuItemRating(long menuItemId) {
        return detail(menuItemId, ratingRepository.averageByMenuItem(Set.of(menuItemId)),
                ratingRepository.countMenuItemStars(menuItemId));
    }

    /** The mapping happens inside the transaction: tags and dishes of the whole page are loaded in two batches. */
    @Transactional(readOnly = true)
    public PageResponse<PublicReviewResponse> restaurantReviews(long restaurantId, int page, int size, String sort) {
        PublicReviewSort order = PublicReviewSort.parse(sort);
        return PageResponse.from(reviewRepository
                .findAll(ReviewSpecifications.forRestaurant(restaurantId), PageRequest.of(page, size, order.sort()))
                .map(reviewMapper::toPublicResponse));
    }

    /**
     * The reviews that rated one dish, newest first: what customers said about the order the dish was in (the
     * comment and tags) and the stars each rated dish got. Reviews of an order with the dish that did not rate it are
     * left out. Same public form as the restaurant's list (short reviewer name, no customer or order).
     */
    @Transactional(readOnly = true)
    public PageResponse<PublicReviewResponse> menuItemReviews(long menuItemId, int page, int size) {
        return PageResponse.from(reviewRepository
                .findAll(ReviewSpecifications.ratesDish(menuItemId),
                        PageRequest.of(page, size, PublicReviewSort.NEWEST.sort()))
                .map(reviewMapper::toPublicResponse));
    }

    // ------------------------------------------------------------------ for search-service

    @Transactional(readOnly = true)
    public RatingFeedResponse restaurantFeed(long afterId, int size) {
        return feed(ratingRepository.averageByRestaurantAfter(afterId, Limit.of(size)), size);
    }

    @Transactional(readOnly = true)
    public RatingFeedResponse menuItemFeed(long afterId, int size) {
        return feed(ratingRepository.averageByMenuItemAfter(afterId, Limit.of(size)), size);
    }

    // ------------------------------------------------------------------ helpers

    private static List<RatingSummaryResponse> summaries(List<RatingAggregate> rows) {
        return rows.stream().map(RatingService::summary).toList();
    }

    private static RatingSummaryResponse summary(RatingAggregate row) {
        return new RatingSummaryResponse(row.getId(), RatingMath.round(row.getAverage()), row.getTotal());
    }

    /** Same rule as catalog-service's feed: a full page may have a next one; a page that is not full is the last. */
    private static RatingFeedResponse feed(List<RatingAggregate> rows, int size) {
        List<RatingSummaryResponse> items = summaries(rows);
        return new RatingFeedResponse(items, items.size() == size ? items.getLast().id() : null);
    }

    /** Both queries run in one read-only transaction (one snapshot), so the average and the counts agree. */
    private static RatingDetailResponse detail(long id, List<RatingAggregate> average, List<RatingCount> stars) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(String.valueOf(star), 0L);
        }
        long count = 0;
        for (RatingCount row : stars) {
            distribution.put(String.valueOf(row.getRating()), row.getTotal());
            count += row.getTotal();
        }
        return new RatingDetailResponse(id, average.isEmpty() ? null : RatingMath.round(average.getFirst().getAverage()),
                count, distribution);
    }
}
