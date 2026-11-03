package com.fooddelivery.restaurantservice.controller;

import java.util.List;

import com.fooddelivery.restaurantservice.dto.response.InternalRestaurantResponse;
import com.fooddelivery.restaurantservice.dto.response.SourceVersionsResponse;
import com.fooddelivery.restaurantservice.service.InternalRestaurantService;
import io.swagger.v3.oas.annotations.Operation;
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
 * Service-to-service API. The API Gateway has no route for /api/v1/internal/**, and the other services must send the
 * shared service token (SecurityConfig).
 */
@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/restaurants")
@RequiredArgsConstructor
public class InternalRestaurantController {

    private final InternalRestaurantService internalRestaurantService;

    @Operation(summary = "Id and version of every restaurant, in id order, one page at a time",
            description = "What search-service compares its index with (afterId = 0 for the first page, then nextAfterId).")
    @GetMapping("/versions")
    public SourceVersionsResponse getVersions(@RequestParam(defaultValue = "0") @Min(0) long afterId,
                                              @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int size) {
        return internalRestaurantService.getVersions(afterId, size);
    }

    @Operation(summary = "Every restaurant of an owner, whatever its status",
            description = "What notification-service sends a restaurant owner the live updates of.")
    @GetMapping(params = "ownerUserId")
    public List<InternalRestaurantResponse> getRestaurantsOfOwner(@RequestParam @Min(1) Long ownerUserId) {
        return internalRestaurantService.getRestaurantsOfOwner(ownerUserId);
    }

    @Operation(summary = "Owner and status of a restaurant, by id")
    @GetMapping("/{restaurantId}")
    public InternalRestaurantResponse getRestaurant(@PathVariable Long restaurantId) {
        return internalRestaurantService.getRestaurant(restaurantId);
    }
}
