package com.fooddelivery.reviewservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.reviewservice.client.OrderLookup;
import com.fooddelivery.reviewservice.client.OrderReviewInfo;
import com.fooddelivery.reviewservice.dto.request.CreateReviewRequest;
import com.fooddelivery.reviewservice.dto.request.DishRatingRequest;
import com.fooddelivery.reviewservice.dto.request.UpdateReviewRequest;
import com.fooddelivery.reviewservice.dto.response.ReviewResponse;
import com.fooddelivery.reviewservice.entity.Review;
import com.fooddelivery.reviewservice.entity.ReviewTag;
import com.fooddelivery.reviewservice.entity.Role;
import com.fooddelivery.reviewservice.exception.BusinessRuleException;
import com.fooddelivery.reviewservice.exception.ConflictException;
import com.fooddelivery.reviewservice.exception.DependencyUnavailableException;
import com.fooddelivery.reviewservice.exception.InvalidRequestException;
import com.fooddelivery.reviewservice.exception.ResourceNotFoundException;
import com.fooddelivery.reviewservice.mapper.ReviewMapper;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import com.fooddelivery.reviewservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** The service's decisions with the repository and order-service replaced by mocks. */
class ReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final CallerInfo CUSTOMER = new CallerInfo(7L, Role.CUSTOMER, "127.0.0.1", "req-1");

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final OrderLookup orderLookup = mock(OrderLookup.class);
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, orderLookup, new ReviewMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
        when(reviewRepository.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static OrderReviewInfo order(long customerId, String status) {
        return new OrderReviewInfo(500L, customerId, "Asha", 10L, "Spice Hub", status,
                List.of(new OrderReviewInfo.Line(101L, "Margherita"), new OrderReviewInfo.Line(102L, "Cola")));
    }

    private static CreateReviewRequest request(String comment, List<ReviewTag> tags, List<DishRatingRequest> dishes) {
        return new CreateReviewRequest(500L, 4, comment, tags, dishes);
    }

    @Test
    void theReviewCopiesNamesFromTheOrderAndTrimsTheComment() {
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(7L, "DELIVERED")));

        ReviewResponse response = reviewService.create(request("  Tasty  ", List.of(ReviewTag.GOOD_VALUE),
                List.of(new DishRatingRequest(102L, 2), new DishRatingRequest(101L, 5))), CUSTOMER);

        assertThat(response.customerName()).isEqualTo("Asha");
        assertThat(response.restaurantName()).isEqualTo("Spice Hub");
        assertThat(response.comment()).isEqualTo("Tasty");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.tags()).containsExactly(ReviewTag.GOOD_VALUE);
        assertThat(response.dishRatings()).extracting("dishName", "rating")
                .containsExactly(tuple("Margherita", 5),
                        tuple("Cola", 2));
    }

    @Test
    void aBlankCommentIsStoredAsNoComment() {
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(7L, "DELIVERED")));

        assertThat(reviewService.create(request("   ", null, null), CUSTOMER).comment()).isNull();
    }

    @Test
    void anUnknownOrderIsNotFound() {
        when(orderLookup.findOrder(500L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(request(null, null, null), CUSTOMER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void anotherCustomersOrderIsNotFound() {
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(99L, "DELIVERED")));

        assertThatThrownBy(() -> reviewService.create(request(null, null, null), CUSTOMER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void anOrderThatIsNotDeliveredIsRefused() {
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(7L, "PREPARING")));

        assertThatThrownBy(() -> reviewService.create(request(null, null, null), CUSTOMER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only delivered orders can be reviewed");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void anOrderThatAlreadyHasAReviewIsAConflict() {
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(7L, "DELIVERED")));
        when(reviewRepository.existsByOrderId(500L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(request(null, null, null), CUSTOMER))
                .isInstanceOf(ConflictException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aBadRequestIsRefusedBeforeOrderServiceIsAsked() {
        List<ReviewTag> tooMany = List.of(ReviewTag.DELICIOUS, ReviewTag.EXCELLENT, ReviewTag.GOOD_SERVICE,
                ReviewTag.FAST_DELIVERY, ReviewTag.GOOD_VALUE);

        assertThatThrownBy(() -> reviewService.create(request(null, tooMany, null), CUSTOMER))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(orderLookup);
    }

    @Test
    void nothingIsSavedWhenOrderServiceIsDown() {
        when(orderLookup.findOrder(500L))
                .thenThrow(new DependencyUnavailableException("order-service", new RuntimeException("refused")));

        assertThatThrownBy(() -> reviewService.create(request(null, null, null), CUSTOMER))
                .isInstanceOf(DependencyUnavailableException.class);
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void myReviewIsOnlyFoundForMyOrder() {
        when(reviewRepository.findByOrderIdAndCustomerId(500L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getMine(500L, CUSTOMER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ edit and delete

    private static Review savedReview(long customerId, long version) {
        Review review = new Review(500L, customerId, "Asha", 10L, "Spice Hub", 4, "Tasty",
                EnumSet.of(ReviewTag.DELICIOUS), NOW.minusSeconds(3600));
        review.addDish(101L, "Margherita", 5);
        review.addDish(102L, "Cola", 2);
        ReflectionTestUtils.setField(review, "version", version);
        return review;
    }

    private static UpdateReviewRequest edit(Long version, List<DishRatingRequest> dishes) {
        return new UpdateReviewRequest(2, "  Cold now ", List.of(ReviewTag.GOOD_VALUE), dishes, version);
    }

    @Test
    void anEditReplacesTheTextTheTagsAndTheDishRatings() {
        Review review = savedReview(7L, 3L);
        when(reviewRepository.findByIdAndCustomerId(9L, 7L)).thenReturn(Optional.of(review));
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(7L, "DELIVERED")));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(call -> call.getArgument(0));

        ReviewResponse response = reviewService.update(9L,
                edit(3L, List.of(new DishRatingRequest(102L, 4))), CUSTOMER);

        assertThat(response.rating()).isEqualTo(2);
        assertThat(response.comment()).isEqualTo("Cold now");
        assertThat(response.tags()).containsExactly(ReviewTag.GOOD_VALUE);
        // 101 is gone, 102 kept its saved name with the new rating
        assertThat(response.dishRatings()).extracting("menuItemId", "dishName", "rating")
                .containsExactly(tuple(102L, "Cola", 4));
        assertThat(response.createdAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.orderId()).isEqualTo(500L);
    }

    @Test
    void aNewDishIsAddedWithTheNameOnTheOrder() {
        Review review = savedReview(7L, 0L);
        when(reviewRepository.findByIdAndCustomerId(9L, 7L)).thenReturn(Optional.of(review));
        OrderReviewInfo order = new OrderReviewInfo(500L, 7L, "Asha", 10L, "Spice Hub", "DELIVERED",
                List.of(new OrderReviewInfo.Line(101L, "Margherita"), new OrderReviewInfo.Line(103L, "Brownie")));
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(call -> call.getArgument(0));

        ReviewResponse response = reviewService.update(9L, edit(null,
                List.of(new DishRatingRequest(103L, 5), new DishRatingRequest(101L, 1))), CUSTOMER);

        assertThat(response.dishRatings()).extracting("menuItemId", "dishName", "rating")
                .containsExactly(tuple(101L, "Margherita", 1), tuple(103L, "Brownie", 5));
    }

    @Test
    void anEditWithAnOutdatedVersionIsAConflict() {
        when(reviewRepository.findByIdAndCustomerId(9L, 7L)).thenReturn(Optional.of(savedReview(7L, 3L)));
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(7L, "DELIVERED")));

        assertThatThrownBy(() -> reviewService.update(9L, edit(2L, null), CUSTOMER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("current version 3, yours 2");
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void anEditOfAnotherCustomersOrUnknownReviewIsNotFoundWithoutAskingOrderService() {
        when(reviewRepository.findByIdAndCustomerId(9L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.update(9L, edit(null, null), CUSTOMER))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(orderLookup);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void anEditIsCheckedLikeANewReview() {
        when(reviewRepository.findByIdAndCustomerId(9L, 7L)).thenReturn(Optional.of(savedReview(7L, 0L)));
        when(orderLookup.findOrder(500L)).thenReturn(Optional.of(order(7L, "DELIVERED")));

        // The same dish twice
        assertThatThrownBy(() -> reviewService.update(9L, edit(null,
                List.of(new DishRatingRequest(101L, 1), new DishRatingRequest(101L, 2))), CUSTOMER))
                .isInstanceOf(InvalidRequestException.class);
        // A dish that is not on the order
        assertThatThrownBy(() -> reviewService.update(9L, edit(null, List.of(new DishRatingRequest(999L, 1))), CUSTOMER))
                .isInstanceOf(BusinessRuleException.class);
        // The order has disappeared from order-service
        when(orderLookup.findOrder(500L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reviewService.update(9L, edit(null, null), CUSTOMER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void myReviewIsDeletedAndAnotherCustomersIsNotFound() {
        Review review = savedReview(7L, 0L);
        when(reviewRepository.findByIdAndCustomerId(9L, 7L)).thenReturn(Optional.of(review));
        when(reviewRepository.findByIdAndCustomerId(8L, 7L)).thenReturn(Optional.empty());

        reviewService.delete(9L, CUSTOMER);

        verify(reviewRepository).delete(review);
        assertThatThrownBy(() -> reviewService.delete(8L, CUSTOMER)).isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, times(1)).delete(any(Review.class));
    }
}
