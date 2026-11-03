package com.fooddelivery.reviewservice.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fooddelivery.reviewservice.client.OrderReviewInfo;
import com.fooddelivery.reviewservice.dto.request.CreateReviewRequest;
import com.fooddelivery.reviewservice.dto.request.DishRatingRequest;
import com.fooddelivery.reviewservice.entity.ReviewTag;
import com.fooddelivery.reviewservice.exception.BusinessRuleException;
import com.fooddelivery.reviewservice.exception.InvalidRequestException;
import com.fooddelivery.reviewservice.exception.ResourceNotFoundException;

/**
 * The rules of writing a review, as plain static methods without database or network: easy to read and to unit test.
 * The service calls them in this order: the request alone first (cheap), the order next. Editing a review follows
 * the same rules as writing it (the overloads that take the tags and dish ratings themselves).
 */
public final class ReviewRules {

    public static final int MAX_TAGS = 4;

    /** The longest name the database stores (customer, restaurant and dish names are copied into the review). */
    public static final int MAX_NAME_LENGTH = 120;

    private ReviewRules() {
    }

    /** What can be checked without asking order-service: tags and dishes are not repeated, at most 4 tags. */
    public static void checkRequest(CreateReviewRequest request) {
        checkRequest(request.tags(), request.dishRatings());
    }

    /** The same check for any request that carries tags and dish ratings (a new review or an edit). */
    public static void checkRequest(List<ReviewTag> requestedTags, List<DishRatingRequest> requestedDishes) {
        List<ReviewTag> tags = requestedTags == null ? List.of() : requestedTags;
        if (tags.size() > MAX_TAGS) {
            throw new InvalidRequestException("At most " + MAX_TAGS + " tags per review");
        }
        if (new HashSet<>(tags).size() != tags.size()) {
            throw new InvalidRequestException("Each tag can be used only once");
        }
        Set<Long> seen = new HashSet<>();
        for (DishRatingRequest dish : dishRatings(requestedDishes)) {
            if (!seen.add(dish.menuItemId())) {
                throw new InvalidRequestException("Dish " + dish.menuItemId() + " is rated more than once");
            }
        }
    }

    /**
     * What needs the order. A foreign order is reported as "not found", like order-service does for its own
     * customers' orders, so nobody can find out which orders exist.
     */
    public static void checkOrder(OrderReviewInfo order, Long callerId, CreateReviewRequest request) {
        checkOrder(order, callerId, request.orderId(), request.dishRatings());
    }

    /** The same check for the order of an existing review (an edit must rate dishes of the original order too). */
    public static void checkOrder(OrderReviewInfo order, Long callerId, Long orderId, List<DishRatingRequest> dishes) {
        if (!callerId.equals(order.customerId())) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        if (!order.isDelivered()) {
            throw new BusinessRuleException("Only delivered orders can be reviewed");
        }
        Set<Long> orderedDishes = new HashSet<>();
        order.lines().forEach(line -> orderedDishes.add(line.menuItemId()));
        for (DishRatingRequest dish : dishRatings(dishes)) {
            if (!orderedDishes.contains(dish.menuItemId())) {
                throw new BusinessRuleException("Dish " + dish.menuItemId() + " is not part of this order");
            }
        }
    }

    /** Trims the comment; an empty one means "no comment". */
    public static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    /** Cuts a copied name to what the database column holds, so a very long name cannot make saving fail. */
    public static String fitName(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    private static List<DishRatingRequest> dishRatings(List<DishRatingRequest> dishes) {
        return dishes == null ? List.of() : dishes;
    }
}
