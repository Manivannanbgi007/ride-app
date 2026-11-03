package com.fooddelivery.deliveryservice.controller;

import java.time.LocalDate;

import com.fooddelivery.deliveryservice.dto.response.RestaurantPartnersResponse;
import com.fooddelivery.deliveryservice.service.RestaurantPartnersService;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurant owners looking at the deliveries of their own restaurants. Requires an access token with the role RESTAURANT_ADMIN.
 * Routed here by the API Gateway even though the path starts with /api/v1/restaurants/mine.
 */
@Tag(name = "Restaurant deliveries", description = "Delivery partners of my restaurant")
@RestController
@RequestMapping("/api/v1/restaurants/mine/{restaurantId}/deliveries")
@RequiredArgsConstructor
public class RestaurantDeliveryController {

    private final RestaurantPartnersService restaurantPartnersService;

    @Operation(summary = "Partners who delivered the most orders of my restaurant in a period (default: today)",
            description = "Delivered orders only, counted by the day they were delivered; at most 366 days")
    @GetMapping("/partners")
    public RestaurantPartnersResponse getPartners(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit,
            CallerInfo caller) {
        return restaurantPartnersService.getPartners(caller.userId(), restaurantId, from, to, limit);
    }
}
