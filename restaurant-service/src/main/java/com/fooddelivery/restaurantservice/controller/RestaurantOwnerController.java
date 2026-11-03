package com.fooddelivery.restaurantservice.controller;

import java.net.URI;
import java.util.List;

import com.fooddelivery.restaurantservice.dto.request.OperatingHoursUpdateRequest;
import com.fooddelivery.restaurantservice.dto.request.RestaurantRequest;
import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.service.RestaurantOwnerService;
import com.fooddelivery.restaurantservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restaurant owners (RESTAURANT_ADMIN) managing their own restaurants. Enforced by RoleAccessInterceptor. */
@Tag(name = "My restaurants", description = "Restaurant owners: onboarding, profile and opening hours")
@RestController
@RequestMapping("/api/v1/restaurants/mine")
@RequiredArgsConstructor
public class RestaurantOwnerController {

    private final RestaurantOwnerService restaurantOwnerService;

    @Operation(summary = "Submit a new restaurant for approval (starts as PENDING)")
    @PostMapping
    public ResponseEntity<RestaurantDetailsResponse> submitRestaurant(@Valid @RequestBody RestaurantRequest request,
                                                                      CallerInfo caller) {
        RestaurantDetailsResponse restaurant = restaurantOwnerService.submitRestaurant(caller.userId(), request);
        return ResponseEntity
                .created(URI.create("/api/v1/restaurants/mine/" + restaurant.id()))
                .body(restaurant);
    }

    @Operation(summary = "List my restaurants (all statuses)")
    @GetMapping
    public List<RestaurantSummaryResponse> getMyRestaurants(CallerInfo caller) {
        return restaurantOwnerService.getMyRestaurants(caller.userId());
    }

    @Operation(summary = "My restaurant, including status, review reason and version")
    @GetMapping("/{restaurantId}")
    public RestaurantDetailsResponse getMyRestaurant(@PathVariable Long restaurantId, CallerInfo caller) {
        return restaurantOwnerService.getMyRestaurant(caller.userId(), restaurantId);
    }

    @Operation(summary = "Update my restaurant (send the version you last read)")
    @PutMapping("/{restaurantId}")
    public RestaurantDetailsResponse updateRestaurant(@PathVariable Long restaurantId,
                                                      @Valid @RequestBody RestaurantRequest request,
                                                      CallerInfo caller) {
        return restaurantOwnerService.updateRestaurant(caller.userId(), restaurantId, request);
    }

    @Operation(summary = "Replace my restaurant's weekly opening hours")
    @PutMapping("/{restaurantId}/operating-hours")
    public RestaurantDetailsResponse updateOperatingHours(@PathVariable Long restaurantId,
                                                          @Valid @RequestBody OperatingHoursUpdateRequest request,
                                                          CallerInfo caller) {
        return restaurantOwnerService.updateOperatingHours(caller.userId(), restaurantId, request);
    }

    @Operation(summary = "Send a REJECTED restaurant for review again")
    @PutMapping("/{restaurantId}/resubmit")
    public RestaurantDetailsResponse resubmitRestaurant(@PathVariable Long restaurantId, CallerInfo caller) {
        return restaurantOwnerService.resubmitRestaurant(caller.userId(), restaurantId);
    }
}
