package com.fooddelivery.orderservice.controller;

import java.time.LocalDate;

import com.fooddelivery.orderservice.dto.request.OrderSearchCriteria;
import com.fooddelivery.orderservice.dto.request.ReasonRequest;
import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.OrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.PageResponse;
import com.fooddelivery.orderservice.dto.response.RestaurantTopItemsResponse;
import com.fooddelivery.orderservice.dto.response.SalesSummaryResponse;
import com.fooddelivery.orderservice.service.RestaurantOrderService;
import com.fooddelivery.orderservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurant owners handling their orders. Requires an access token with the role RESTAURANT_ADMIN.
 * Routed here by the API Gateway even though the path starts with /api/v1/restaurants/mine.
 */
@Tag(name = "Restaurant orders", description = "Accept or reject orders and update preparation status")
@RestController
@RequestMapping("/api/v1/restaurants/mine/{restaurantId}/orders")
@RequiredArgsConstructor
public class RestaurantOrderController {

    private final RestaurantOrderService restaurantOrderService;

    @Operation(summary = "Orders of my restaurant (status and date filters)",
            description = "New orders waiting for a decision: ?status=PLACED&sort=createdAt,asc")
    @GetMapping
    public PageResponse<OrderSummaryResponse> getOrders(
            @PathVariable Long restaurantId,
            @Valid @ParameterObject OrderSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            CallerInfo caller) {
        return restaurantOrderService.getOrders(caller.userId(), restaurantId, criteria, pageable);
    }

    @Operation(summary = "Sales information for a period (default: today)")
    @GetMapping("/sales-summary")
    public SalesSummaryResponse getSalesSummary(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            CallerInfo caller) {
        return restaurantOrderService.getSalesSummary(caller.userId(), restaurantId, fromDate, toDate);
    }

    @Operation(summary = "Most ordered dishes of my restaurant in a period (default: today)",
            description = "Ranked by quantity. Counts every order except unpaid ones, like the sales summary.")
    @GetMapping("/top-items")
    public RestaurantTopItemsResponse getTopItems(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit,
            CallerInfo caller) {
        return restaurantOrderService.getTopItems(caller.userId(), restaurantId, from, to, limit);
    }

    @Operation(summary = "One order with its timeline")
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long restaurantId, @PathVariable Long orderId, CallerInfo caller) {
        return restaurantOrderService.getOrder(caller.userId(), restaurantId, orderId);
    }

    @Operation(summary = "Accept a new order (PLACED -> ACCEPTED)")
    @PutMapping("/{orderId}/accept")
    public OrderResponse acceptOrder(@PathVariable Long restaurantId, @PathVariable Long orderId, CallerInfo caller) {
        return restaurantOrderService.acceptOrder(caller.userId(), restaurantId, orderId);
    }

    @Operation(summary = "Reject a new order (PLACED -> REJECTED, reason shown to the customer)")
    @PutMapping("/{orderId}/reject")
    public OrderResponse rejectOrder(@PathVariable Long restaurantId, @PathVariable Long orderId,
                                     @Valid @RequestBody ReasonRequest request, CallerInfo caller) {
        return restaurantOrderService.rejectOrder(caller.userId(), restaurantId, orderId, request.reason());
    }

    @Operation(summary = "Start preparing (ACCEPTED -> PREPARING)")
    @PutMapping("/{orderId}/preparing")
    public OrderResponse markPreparing(@PathVariable Long restaurantId, @PathVariable Long orderId, CallerInfo caller) {
        return restaurantOrderService.markPreparing(caller.userId(), restaurantId, orderId);
    }

    @Operation(summary = "Food is ready for pickup (PREPARING -> READY_FOR_PICKUP)")
    @PutMapping("/{orderId}/ready")
    public OrderResponse markReady(@PathVariable Long restaurantId, @PathVariable Long orderId, CallerInfo caller) {
        return restaurantOrderService.markReadyForPickup(caller.userId(), restaurantId, orderId);
    }
}
