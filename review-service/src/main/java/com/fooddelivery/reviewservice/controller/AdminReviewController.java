package com.fooddelivery.reviewservice.controller;

import com.fooddelivery.reviewservice.dto.request.ReviewFilter;
import com.fooddelivery.reviewservice.dto.request.ReviewSearchCriteria;
import com.fooddelivery.reviewservice.dto.request.ReviewSummaryFilter;
import com.fooddelivery.reviewservice.dto.response.CustomerLikedDishesResponse;
import com.fooddelivery.reviewservice.dto.response.FeaturedDishResponse;
import com.fooddelivery.reviewservice.dto.response.PageResponse;
import com.fooddelivery.reviewservice.dto.response.ReviewResponse;
import com.fooddelivery.reviewservice.dto.response.ReviewSummaryResponse;
import com.fooddelivery.reviewservice.service.CustomerLikedDishesService;
import com.fooddelivery.reviewservice.service.FeaturedDishService;
import com.fooddelivery.reviewservice.service.LikedDishPeriod;
import com.fooddelivery.reviewservice.service.ReviewAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: reviews",
        description = "Read the reviews, their summary and the top-rated dishes (PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewAdminService reviewAdminService;
    private final FeaturedDishService featuredDishService;
    private final CustomerLikedDishesService customerLikedDishesService;

    @Operation(summary = "Search reviews",
            description = "Filters: restaurantId, menuItemId (reviews that rate the dish), rating (overall), tag, from, to. "
                    + "Sort: createdAt,desc (default), createdAt,asc, rating,desc, rating,asc. Page size at most 50")
    @GetMapping
    public PageResponse<ReviewResponse> search(
            @Valid @ParameterObject ReviewSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return reviewAdminService.search(criteria, pageable);
    }

    @Operation(summary = "Number of reviews, average rating and stars distribution",
            description = "Filters: restaurantId, menuItemId, from, to. With menuItemId the numbers are the dish's own ratings")
    @GetMapping("/summary")
    public ReviewSummaryResponse summary(@ParameterObject ReviewSummaryFilter filter) {
        return reviewAdminService.summary(filter);
    }

    @Operation(summary = "Top-rated dishes",
            description = "One row per dish rated in the period, best average first (then most ratings). "
                    + "Page size at most 12")
    @GetMapping("/featured-dishes")
    public PageResponse<FeaturedDishResponse> featuredDishes(
            @ParameterObject ReviewFilter filter,
            @Parameter(description = "Page number, from 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Dishes per page, at most 12")
            @RequestParam(defaultValue = "3") @Min(1) @Max(FeaturedDishService.MAX_PAGE_SIZE) int size) {
        return featuredDishService.featuredDishes(filter, page, size);
    }

    @Operation(summary = "Dishes a customer rated highest",
            description = "Best average first (then most ratings, most recent). period ALL (default), MONTHLY "
                    + "(last 30 days) or WEEKLY (last 7 days); limit 1-20, default 7")
    @GetMapping("/customers/{customerId}/liked-dishes")
    public CustomerLikedDishesResponse likedDishes(@PathVariable long customerId,
            @RequestParam(defaultValue = "ALL") LikedDishPeriod period,
            @RequestParam(defaultValue = "7") @Min(1) @Max(20) int limit) {
        return customerLikedDishesService.likedDishes(customerId, period, limit);
    }
}
