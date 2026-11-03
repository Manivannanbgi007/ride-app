package com.fooddelivery.reviewservice.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fooddelivery.reviewservice.client.OrderLookup;
import com.fooddelivery.reviewservice.client.OrderReviewInfo;
import com.fooddelivery.reviewservice.dto.request.CreateReviewRequest;
import com.fooddelivery.reviewservice.dto.request.DishRatingRequest;
import com.fooddelivery.reviewservice.dto.request.UpdateReviewRequest;
import com.fooddelivery.reviewservice.dto.response.ReviewResponse;
import com.fooddelivery.reviewservice.entity.Review;
import com.fooddelivery.reviewservice.entity.ReviewTag;
import com.fooddelivery.reviewservice.exception.ConflictException;
import com.fooddelivery.reviewservice.exception.ResourceNotFoundException;
import com.fooddelivery.reviewservice.mapper.ReviewMapper;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import com.fooddelivery.reviewservice.web.CallerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The customer's side: write a review of a delivered order, read it back, edit it and delete it.
 * <p>
 * order-service is asked <em>before</em> the database transaction starts, so no database connection waits on
 * another service (the same rule as order-service's checkout).
 */
@Slf4j
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderLookup orderLookup;
    private final ReviewMapper reviewMapper;
    private final Clock clock;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public ReviewService(ReviewRepository reviewRepository, OrderLookup orderLookup, ReviewMapper reviewMapper,
                         Clock clock, PlatformTransactionManager transactionManager) {
        this.reviewRepository = reviewRepository;
        this.orderLookup = orderLookup;
        this.reviewMapper = reviewMapper;
        this.clock = clock;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public ReviewResponse create(CreateReviewRequest request, CallerInfo caller) {
        ReviewRules.checkRequest(request);

        OrderReviewInfo order = orderLookup.findOrder(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId()));
        ReviewRules.checkOrder(order, caller.userId(), request);

        ReviewResponse created = writeTransaction.execute(status -> {
            // The unique key on order_id settles two requests at the same moment (the loser gets 409 as well)
            if (reviewRepository.existsByOrderId(order.id())) {
                throw new ConflictException("This order has already been reviewed");
            }
            return reviewMapper.toResponse(reviewRepository.save(build(request, order)));
        });
        log.info("Review of order {} saved: rating {}", order.id(), request.rating());
        return created;
    }

    /** The caller's own review of the order; 404 when there is none (the storefront then shows the form). */
    public ReviewResponse getMine(Long orderId, CallerInfo caller) {
        return readTransaction.execute(status -> reviewRepository
                .findByOrderIdAndCustomerId(orderId, caller.userId())
                .map(reviewMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No review for order " + orderId)));
    }

    /**
     * Edits the caller's own review with the rules of writing one: the dishes must be dishes of the original order
     * (asked again), and the dish ratings sent replace the saved ones. Someone else's review is "not found".
     * A {@code version} in the request that is no longer the review's version is a conflict (409).
     */
    public ReviewResponse update(Long reviewId, UpdateReviewRequest request, CallerInfo caller) {
        ReviewRules.checkRequest(request.tags(), request.dishRatings());

        Long orderId = findOwn(reviewId, caller).getOrderId();
        OrderReviewInfo order = orderLookup.findOrder(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        ReviewRules.checkOrder(order, caller.userId(), orderId, request.dishRatings());

        ReviewResponse updated = writeTransaction.execute(status -> {
            // Read again inside the transaction: the review may have been edited or deleted in the meantime
            Review review = findOwn(reviewId, caller);
            if (request.version() != null && !request.version().equals(review.getVersion())) {
                throw new ConflictException("The review was changed by another request (current version "
                        + review.getVersion() + ", yours " + request.version() + "). Reload it and try again.");
            }
            apply(review, request, order);
            // Flushed here, so the response carries the new version and a lost race becomes a 409
            return reviewMapper.toResponse(reviewRepository.saveAndFlush(review));
        });
        log.info("Review {} of order {} edited: rating {}", reviewId, orderId, request.rating());
        return updated;
    }

    /** Deletes the caller's own review; the order can be reviewed again. Someone else's review is "not found". */
    public void delete(Long reviewId, CallerInfo caller) {
        writeTransaction.executeWithoutResult(status -> reviewRepository.delete(findOwn(reviewId, caller)));
        log.info("Review {} deleted", reviewId);
    }

    /** 404 also when the review exists but is someone else's: nobody can find out which reviews exist. */
    private Review findOwn(Long reviewId, CallerInfo caller) {
        return reviewRepository.findByIdAndCustomerId(reviewId, caller.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
    }

    private Review build(CreateReviewRequest request, OrderReviewInfo order) {
        Review review = new Review(order.id(), order.customerId(),
                ReviewRules.fitName(order.customerName()), order.restaurantId(),
                ReviewRules.fitName(order.restaurantName()), request.rating(),
                ReviewRules.normalizeComment(request.comment()), tagSet(request.tags()), now());

        if (request.dishRatings() != null) {
            Map<Long, String> dishNames = dishNames(order);
            for (DishRatingRequest dish : request.dishRatings()) {
                review.addDish(dish.menuItemId(), ReviewRules.fitName(dishNames.get(dish.menuItemId())), dish.rating());
            }
        }
        return review;
    }

    private void apply(Review review, UpdateReviewRequest request, OrderReviewInfo order) {
        review.edit(request.rating(), ReviewRules.normalizeComment(request.comment()), tagSet(request.tags()), now());

        List<DishRatingRequest> dishes = request.dishRatings() == null ? List.of() : request.dishRatings();
        Set<Long> kept = new HashSet<>();
        dishes.forEach(dish -> kept.add(dish.menuItemId()));
        // Dishes the customer left out are no longer rated
        review.keepDishes(kept);
        Map<Long, String> dishNames = dishNames(order);
        for (DishRatingRequest dish : dishes) {
            review.rateDish(dish.menuItemId(), ReviewRules.fitName(dishNames.get(dish.menuItemId())), dish.rating());
        }
    }

    /** Truncated to microseconds, what the database column keeps: the response shows exactly what is stored. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static Set<ReviewTag> tagSet(List<ReviewTag> tags) {
        List<ReviewTag> requested = tags == null ? List.of() : tags;
        return requested.isEmpty() ? EnumSet.noneOf(ReviewTag.class) : EnumSet.copyOf(requested);
    }

    /** The name on the order is saved with the rating (a later rename of the dish changes nothing here). */
    private static Map<Long, String> dishNames(OrderReviewInfo order) {
        Map<Long, String> dishNames = new HashMap<>();
        order.lines().forEach(line -> dishNames.putIfAbsent(line.menuItemId(), line.name()));
        return dishNames;
    }
}
