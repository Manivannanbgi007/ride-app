package com.fooddelivery.reviewservice.controller;

import java.util.List;

import com.fooddelivery.reviewservice.dto.response.PageResponse;
import com.fooddelivery.reviewservice.dto.response.PublicReviewResponse;
import com.fooddelivery.reviewservice.dto.response.RatingDetailResponse;
import com.fooddelivery.reviewservice.dto.response.RatingSummaryResponse;
import com.fooddelivery.reviewservice.service.RatingService;
import com.fooddelivery.reviewservice.validation.IdListParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public: anyone may read the ratings, signed in or not. No role is needed and no identity header is read (the
 * role interceptors cover only /api/v1/reviews and /api/v1/admin/reviews).
 */
@Tag(name = "Ratings", description = "Average rating, stars and reviews of restaurants and dishes (public, no sign-in)")
@RestController
@RequestMapping("/api/v1/ratings")
@RequiredArgsConstructor
public class RatingController {

    /** The most ids one request may ask about. */
    public static final int MAX_IDS = 100;

    private final RatingService ratingService;

    @Operation(summary = "Ratings of several restaurants",
            description = "ids = 1 to 100 comma-separated restaurant ids, else 400. Only restaurants with at least one "
                    + "review are in the answer. Average: one decimal, half up")
    @GetMapping("/restaurants")
    public List<RatingSummaryResponse> restaurants(@RequestParam String ids) {
        return ratingService.restaurantRatings(IdListParser.parse(ids, MAX_IDS));
    }

    @Operation(summary = "Ratings of several dishes",
            description = "ids = 1 to 100 comma-separated menu item ids, else 400. Only dishes with at least one rating "
                    + "are in the answer. Average: one decimal, half up")
    @GetMapping("/menu-items")
    public List<RatingSummaryResponse> menuItems(@RequestParam String ids) {
        return ratingService.menuItemRatings(IdListParser.parse(ids, MAX_IDS));
    }

    @Operation(summary = "Rating of one restaurant with the number of reviews per star",
            description = "No reviews yet: count 0, average null and every star 0 (not a 404)")
    @GetMapping("/restaurants/{restaurantId}")
    public RatingDetailResponse restaurant(@PathVariable @Min(1) long restaurantId) {
        return ratingService.restaurantRating(restaurantId);
    }

    @Operation(summary = "Rating of one dish with the number of ratings per star",
            description = "No ratings yet: count 0, average null and every star 0 (not a 404)")
    @GetMapping("/menu-items/{menuItemId}")
    public RatingDetailResponse menuItem(@PathVariable @Min(1) long menuItemId) {
        return ratingService.menuItemRating(menuItemId);
    }

    @Operation(summary = "Reviews of a restaurant",
            description = "sort = newest (default), highest or lowest. Page size 1 to 50. The reviewer is shown as first "
                    + "name and initial of the last name; the customer and the order are not exposed")
    @GetMapping("/restaurants/{restaurantId}/reviews")
    public PageResponse<PublicReviewResponse> restaurantReviews(
            @PathVariable @Min(1) long restaurantId,
            @Parameter(description = "Page number, from 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Reviews per page, 1 to 50") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @Parameter(description = "newest, highest or lowest") @RequestParam(defaultValue = "newest") String sort) {
        return ratingService.restaurantReviews(restaurantId, page, size, sort);
    }

    @Operation(summary = "Reviews that rated a dish",
            description = "Newest first. Page size 1 to 50. Each one is a customer's review of an order with this dish "
                    + "that gave the dish stars: its comment, its tags and the stars of every rated dish. The reviewer "
                    + "is shown as first name and initial of the last name; the customer and the order are not exposed")
    @GetMapping("/menu-items/{menuItemId}/reviews")
    public PageResponse<PublicReviewResponse> menuItemReviews(
            @PathVariable @Min(1) long menuItemId,
            @Parameter(description = "Page number, from 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Reviews per page, 1 to 50") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ratingService.menuItemReviews(menuItemId, page, size);
    }
}
