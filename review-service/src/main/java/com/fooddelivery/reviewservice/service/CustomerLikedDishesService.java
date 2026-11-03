package com.fooddelivery.reviewservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.reviewservice.dto.response.CustomerLikedDishesResponse;
import com.fooddelivery.reviewservice.repository.LikedDishRow;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dishes one customer rated highest, for the admin customer screen. The database groups and ranks the
 * customer's dish ratings in one query (best average, then most ratings, then most recent); a second query counts
 * all the ratings of the period. An unknown customer simply has none.
 */
@Service
@RequiredArgsConstructor
public class CustomerLikedDishesService {

    private final ReviewRepository reviewRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CustomerLikedDishesResponse likedDishes(long customerId, LikedDishPeriod period, int limit) {
        Instant from = period.start(clock.instant());
        List<CustomerLikedDishesResponse.Dish> dishes = reviewRepository
                .findLikedDishes(customerId, from, Limit.of(limit)).stream()
                .map(CustomerLikedDishesService::toDish)
                .toList();
        long ratingCount = dishes.isEmpty() ? 0 : reviewRepository.countDishRatingsOfCustomer(customerId, from);
        return new CustomerLikedDishesResponse(customerId, period.name(), ratingCount, dishes);
    }

    private static CustomerLikedDishesResponse.Dish toDish(LikedDishRow row) {
        return new CustomerLikedDishesResponse.Dish(row.menuItemId(), row.dishName(),
                BigDecimal.valueOf(row.averageRating()).setScale(1, RoundingMode.HALF_UP), row.ratingCount(),
                row.lastRatedAt());
    }
}
