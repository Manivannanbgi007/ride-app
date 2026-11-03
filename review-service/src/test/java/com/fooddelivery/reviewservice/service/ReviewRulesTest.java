package com.fooddelivery.reviewservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fooddelivery.reviewservice.client.OrderReviewInfo;
import com.fooddelivery.reviewservice.dto.request.CreateReviewRequest;
import com.fooddelivery.reviewservice.dto.request.DishRatingRequest;
import com.fooddelivery.reviewservice.entity.ReviewTag;
import com.fooddelivery.reviewservice.exception.BusinessRuleException;
import com.fooddelivery.reviewservice.exception.InvalidRequestException;
import com.fooddelivery.reviewservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

/** The rules of writing a review: no database, no network. */
class ReviewRulesTest {

    private static final long CUSTOMER = 7L;

    private static OrderReviewInfo order(long customerId, String status) {
        return new OrderReviewInfo(500L, customerId, "Asha", 10L, "Spice Hub", status,
                List.of(new OrderReviewInfo.Line(101L, "Margherita"), new OrderReviewInfo.Line(102L, "Cola")));
    }

    private static CreateReviewRequest request(List<ReviewTag> tags, List<DishRatingRequest> dishes) {
        return new CreateReviewRequest(500L, 5, null, tags, dishes);
    }

    @Test
    void aDeliveredOrderOfTheCallerCanBeReviewed() {
        CreateReviewRequest request = request(List.of(ReviewTag.DELICIOUS),
                List.of(new DishRatingRequest(101L, 5), new DishRatingRequest(102L, 3)));

        assertThatCode(() -> {
            ReviewRules.checkRequest(request);
            ReviewRules.checkOrder(order(CUSTOMER, "DELIVERED"), CUSTOMER, request);
        }).doesNotThrowAnyException();
    }

    @Test
    void onlyDeliveredOrdersCanBeReviewed() {
        CreateReviewRequest request = request(null, null);

        assertThatThrownBy(() -> ReviewRules.checkOrder(order(CUSTOMER, "OUT_FOR_DELIVERY"), CUSTOMER, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only delivered orders can be reviewed");
    }

    @Test
    void anotherCustomersOrderLooksLikeAnOrderThatDoesNotExist() {
        CreateReviewRequest request = request(null, null);

        assertThatThrownBy(() -> ReviewRules.checkOrder(order(99L, "DELIVERED"), CUSTOMER, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aRatedDishMustBeALineOfTheOrder() {
        CreateReviewRequest request = request(null, List.of(new DishRatingRequest(999L, 4)));

        assertThatThrownBy(() -> ReviewRules.checkOrder(order(CUSTOMER, "DELIVERED"), CUSTOMER, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("999");
    }

    @Test
    void aDishCanBeRatedOnlyOnce() {
        CreateReviewRequest request = request(null,
                List.of(new DishRatingRequest(101L, 4), new DishRatingRequest(101L, 5)));

        assertThatThrownBy(() -> ReviewRules.checkRequest(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("101");
    }

    @Test
    void atMostFourTags() {
        List<ReviewTag> fiveTags = List.of(ReviewTag.DELICIOUS, ReviewTag.EXCELLENT, ReviewTag.GOOD_SERVICE,
                ReviewTag.FAST_DELIVERY, ReviewTag.GOOD_VALUE);
        assertThatThrownBy(() -> ReviewRules.checkRequest(request(fiveTags, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("At most 4 tags per review");

        assertThatCode(() -> ReviewRules.checkRequest(request(fiveTags.subList(0, 4), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void aTagCanBeUsedOnlyOnce() {
        assertThatThrownBy(() -> ReviewRules.checkRequest(
                request(List.of(ReviewTag.DELICIOUS, ReviewTag.DELICIOUS), null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Each tag can be used only once");
    }

    @Test
    void commentsAreTrimmedAndBlankMeansNone() {
        assertThat(ReviewRules.normalizeComment("  Great food \n")).isEqualTo("Great food");
        assertThat(ReviewRules.normalizeComment("   ")).isNull();
        assertThat(ReviewRules.normalizeComment("")).isNull();
        assertThat(ReviewRules.normalizeComment(null)).isNull();
    }

    @Test
    void copiedNamesAreCutToTheColumnLength() {
        assertThat(ReviewRules.fitName("Pizza")).isEqualTo("Pizza");
        assertThat(ReviewRules.fitName("x".repeat(200))).hasSize(ReviewRules.MAX_NAME_LENGTH);
    }

    @Test
    void anEditIsCheckedWithTheSameRulesAsANewReview() {
        List<DishRatingRequest> ratedTwice = List.of(new DishRatingRequest(101L, 4), new DishRatingRequest(101L, 5));
        assertThatThrownBy(() -> ReviewRules.checkRequest(List.of(ReviewTag.DELICIOUS), ratedTwice))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("101");
        assertThatThrownBy(() -> ReviewRules.checkRequest(List.of(ReviewTag.DELICIOUS, ReviewTag.DELICIOUS), null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> ReviewRules.checkRequest(null, null)).doesNotThrowAnyException();

        OrderReviewInfo delivered = order(CUSTOMER, "DELIVERED");
        assertThatCode(() -> ReviewRules.checkOrder(delivered, CUSTOMER, 500L, List.of(new DishRatingRequest(102L, 1))))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReviewRules.checkOrder(delivered, CUSTOMER, 500L, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ReviewRules.checkOrder(delivered, CUSTOMER, 500L, List.of(new DishRatingRequest(5L, 1))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ReviewRules.checkOrder(delivered, 99L, 500L, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found: 500");
    }
}
