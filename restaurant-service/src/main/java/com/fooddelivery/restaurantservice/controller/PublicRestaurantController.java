package com.fooddelivery.restaurantservice.controller;

import com.fooddelivery.restaurantservice.dto.request.RestaurantSearchCriteria;
import com.fooddelivery.restaurantservice.dto.response.PageResponse;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.service.PublicRestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer-facing restaurant browsing. No identity headers needed. */
@Tag(name = "Restaurants", description = "Browse active restaurants")
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class PublicRestaurantController {

    private final PublicRestaurantService publicRestaurantService;

    @Operation(summary = "List active restaurants",
            description = "Example: ?city=Bengaluru&cuisine=Biryani&search=spice&page=0&size=20&sort=name,asc")
    @GetMapping
    public PageResponse<PublicRestaurantSummaryResponse> browseRestaurants(
            @Valid @ParameterObject RestaurantSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return publicRestaurantService.browseRestaurants(criteria, pageable);
    }

    @Operation(summary = "Restaurant page: details, opening hours and whether it is open now")
    @GetMapping("/{restaurantId}")
    public PublicRestaurantDetailsResponse getRestaurant(@PathVariable Long restaurantId) {
        return publicRestaurantService.getRestaurant(restaurantId);
    }
}
