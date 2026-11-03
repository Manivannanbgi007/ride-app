package com.fooddelivery.reviewservice.controller;

import com.fooddelivery.reviewservice.dto.response.RatingFeedResponse;
import com.fooddelivery.reviewservice.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API for search-service: the ratings of every restaurant and dish, one page at a time. The API
 * Gateway has no route for /api/v1/internal/**, so these endpoints are reachable only from inside the platform
 * network (like catalog-service's search feed, they check no role).
 */
@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/ratings")
@RequiredArgsConstructor
public class InternalRatingController {

    private final RatingService ratingService;

    @Operation(summary = "Rating of every restaurant that has reviews, in id order, one page at a time",
            description = "afterId = 0 for the first page, then nextAfterId.")
    @GetMapping("/restaurants")
    public RatingFeedResponse restaurantFeed(@RequestParam(defaultValue = "0") @Min(0) long afterId,
                                             @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int size) {
        return ratingService.restaurantFeed(afterId, size);
    }

    @Operation(summary = "Rating of every dish that has ratings, in id order, one page at a time")
    @GetMapping("/menu-items")
    public RatingFeedResponse menuItemFeed(@RequestParam(defaultValue = "0") @Min(0) long afterId,
                                           @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int size) {
        return ratingService.menuItemFeed(afterId, size);
    }
}
