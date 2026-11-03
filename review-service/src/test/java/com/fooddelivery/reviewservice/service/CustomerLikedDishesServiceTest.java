package com.fooddelivery.reviewservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.reviewservice.dto.response.CustomerLikedDishesResponse;
import com.fooddelivery.reviewservice.repository.LikedDishRow;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

class CustomerLikedDishesServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final CustomerLikedDishesService service =
            new CustomerLikedDishesService(reviewRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundsTheAverageToOneDecimalAndKeepsTheDatabaseOrder() {
        when(reviewRepository.findLikedDishes(eq(7L), eq(null), any())).thenReturn(List.of(
                new LikedDishRow(2L, "Biryani", 4.6666, 3L, Instant.parse("2026-10-08T10:00:00Z")),
                new LikedDishRow(1L, "Kulfi", 4.0, 1L, Instant.parse("2026-10-01T10:00:00Z"))));
        when(reviewRepository.countDishRatingsOfCustomer(7L, null)).thenReturn(9L);

        CustomerLikedDishesResponse response = service.likedDishes(7L, LikedDishPeriod.ALL, 7);

        assertThat(response.period()).isEqualTo("ALL");
        assertThat(response.ratingCount()).isEqualTo(9);
        assertThat(response.dishes()).extracting(CustomerLikedDishesResponse.Dish::menuItemId).containsExactly(2L, 1L);
        assertThat(response.dishes().getFirst().averageRating()).isEqualByComparingTo("4.7");
        assertThat(response.dishes().getFirst().ratingCount()).isEqualTo(3);
        verify(reviewRepository).findLikedDishes(7L, null, Limit.of(7));
    }

    @Test
    void monthlyAndWeeklyLookBackThirtyAndSevenDays() {
        when(reviewRepository.findLikedDishes(eq(7L), any(), any())).thenReturn(List.of());

        service.likedDishes(7L, LikedDishPeriod.MONTHLY, 3);
        service.likedDishes(7L, LikedDishPeriod.WEEKLY, 3);

        verify(reviewRepository).findLikedDishes(7L, Instant.parse("2026-09-09T10:00:00Z"), Limit.of(3));
        verify(reviewRepository).findLikedDishes(7L, Instant.parse("2026-10-02T10:00:00Z"), Limit.of(3));
    }

    @Test
    void aCustomerWithoutRatingsGetsAnEmptyListAndZero() {
        when(reviewRepository.findLikedDishes(eq(99L), eq(null), any())).thenReturn(List.of());

        CustomerLikedDishesResponse response = service.likedDishes(99L, LikedDishPeriod.ALL, 7);

        assertThat(response.dishes()).isEmpty();
        assertThat(response.ratingCount()).isZero();
    }
}
