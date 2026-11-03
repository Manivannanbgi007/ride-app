package com.fooddelivery.reviewservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.fooddelivery.reviewservice.dto.response.RatingDetailResponse;
import com.fooddelivery.reviewservice.dto.response.RatingFeedResponse;
import com.fooddelivery.reviewservice.dto.response.RatingSummaryResponse;
import com.fooddelivery.reviewservice.mapper.ReviewMapper;
import com.fooddelivery.reviewservice.repository.RatingAggregate;
import com.fooddelivery.reviewservice.repository.RatingCount;
import com.fooddelivery.reviewservice.repository.RatingRepository;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

/** The numbers are made from what the repository answers: rounding, the feed's paging rule and the empty cases. */
class RatingServiceTest {

    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final RatingService ratingService =
            new RatingService(ratingRepository, mock(ReviewRepository.class), new ReviewMapper());

    /** Plain stand-ins for the rows the database returns (a mock inside another stubbing would break Mockito). */
    private record AggregateRow(Long getId, Double getAverage, long getTotal) implements RatingAggregate {
    }

    private record StarRow(int getRating, long getTotal) implements RatingCount {
    }

    private static RatingAggregate aggregate(long id, double average, long total) {
        return new AggregateRow(id, average, total);
    }

    private static RatingCount stars(int rating, long total) {
        return new StarRow(rating, total);
    }

    @Test
    void averagesAreRoundedHalfUpToOneDecimal() {
        when(ratingRepository.averageByRestaurant(Set.of(1L, 2L)))
                .thenReturn(List.of(aggregate(1L, 4.25, 4), aggregate(2L, 4.3333, 3)));

        List<RatingSummaryResponse> ratings = ratingService.restaurantRatings(Set.of(1L, 2L));

        assertThat(ratings).extracting("id", "average", "count")
                .containsExactly(tuple(1L, new BigDecimal("4.3"), 4L), tuple(2L, new BigDecimal("4.3"), 3L));
    }

    @Test
    void dishRatingsComeFromTheDishQuery() {
        when(ratingRepository.averageByMenuItem(Set.of(5L))).thenReturn(List.of(aggregate(5L, 3.0, 2)));

        assertThat(ratingService.menuItemRatings(Set.of(5L))).extracting("id", "average", "count")
                .containsExactly(tuple(5L, new BigDecimal("3.0"), 2L));
    }

    @Test
    void aRestaurantWithoutReviewsHasNoAverageAndZeroStars() {
        when(ratingRepository.averageByRestaurant(Set.of(9L))).thenReturn(List.of());
        when(ratingRepository.countRestaurantStars(9L)).thenReturn(List.of());

        RatingDetailResponse detail = ratingService.restaurantRating(9L);

        assertThat(detail.id()).isEqualTo(9L);
        assertThat(detail.average()).isNull();
        assertThat(detail.count()).isZero();
        assertThat(detail.distribution()).containsOnlyKeys("1", "2", "3", "4", "5").containsValues(0L);
    }

    @Test
    void theDistributionAndTheCountComeFromTheStarCounts() {
        when(ratingRepository.averageByMenuItem(Set.of(7L))).thenReturn(List.of(aggregate(7L, 4.2, 5)));
        when(ratingRepository.countMenuItemStars(7L)).thenReturn(List.of(stars(5, 3), stars(2, 1), stars(4, 1)));

        RatingDetailResponse detail = ratingService.menuItemRating(7L);

        assertThat(detail.average()).isEqualByComparingTo("4.2");
        assertThat(detail.count()).isEqualTo(5);
        assertThat(detail.distribution()).containsExactly(
                entry("1", 0L), entry("2", 1L),
                entry("3", 0L), entry("4", 1L),
                entry("5", 3L));
    }

    @Test
    void aFullFeedPageNamesTheNextStart() {
        when(ratingRepository.averageByRestaurantAfter(any(Long.class), any(Limit.class)))
                .thenReturn(List.of(aggregate(4L, 5.0, 1), aggregate(8L, 2.5, 2)));

        RatingFeedResponse feed = ratingService.restaurantFeed(3L, 2);

        verify(ratingRepository).averageByRestaurantAfter(3L, Limit.of(2));
        assertThat(feed.items()).extracting("id", "average", "count")
                .containsExactly(tuple(4L, new BigDecimal("5.0"), 1L), tuple(8L, new BigDecimal("2.5"), 2L));
        assertThat(feed.nextAfterId()).isEqualTo(8L);
    }

    @Test
    void aPageThatIsNotFullOrIsEmptyIsTheLastOne() {
        when(ratingRepository.averageByMenuItemAfter(any(Long.class), any(Limit.class)))
                .thenReturn(List.of(aggregate(4L, 5.0, 1)))
                .thenReturn(List.of());

        assertThat(ratingService.menuItemFeed(0L, 1000).nextAfterId()).isNull();
        RatingFeedResponse empty = ratingService.menuItemFeed(4L, 1000);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.nextAfterId()).isNull();
    }
}
