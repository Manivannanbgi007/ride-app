package com.fooddelivery.orderservice.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import com.fooddelivery.orderservice.dto.request.PlaceOrderRequest;
import com.fooddelivery.orderservice.dto.request.ReasonRequest;
import com.fooddelivery.orderservice.dto.response.CustomerOrderActivityResponse;
import com.fooddelivery.orderservice.dto.response.CustomerOrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.FavouriteDishResponse;
import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.OrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.PageResponse;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.service.CustomerInsightsService;
import com.fooddelivery.orderservice.service.CustomerOrderService;
import com.fooddelivery.orderservice.service.OrderPlacementService;
import com.fooddelivery.orderservice.service.OrderPlacementService.PlacementResult;
import com.fooddelivery.orderservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The calling customer's orders. Requires an access token with the role CUSTOMER. */
@Tag(name = "My orders", description = "Checkout, tracking, history and cancellation")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replayed";

    private final OrderPlacementService orderPlacementService;
    private final CustomerOrderService customerOrderService;
    private final CustomerInsightsService customerInsightsService;

    @Operation(summary = "Place an order from my cart",
            description = "Send a unique Idempotency-Key per checkout (for example a UUID). Retrying with the same key "
                    + "returns the same order (200 + Idempotent-Replayed: true) instead of creating a second one.")
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Parameter(description = "Unique key for this checkout attempt (8-64 letters, digits, - or _)", required = true)
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            CallerInfo caller) {
        PlacementResult result = orderPlacementService.placeOrder(caller.userId(), idempotencyKey, request);

        if (result.replayed()) {
            return ResponseEntity.ok().header(IDEMPOTENT_REPLAY_HEADER, "true").body(result.order());
        }
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.order().id())).body(result.order());
    }

    @Operation(summary = "My order history, newest first")
    @GetMapping
    public PageResponse<OrderSummaryResponse> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            CallerInfo caller) {
        return customerOrderService.getMyOrders(caller.userId(), status, pageable);
    }

    @Operation(summary = "My orders in numbers, all time",
            description = "Every order counts in totalOrders; active = not finished yet (waiting for payment "
                    + "included); totalSpent = what the delivered orders cost")
    @GetMapping("/summary")
    public CustomerOrderSummaryResponse getMySummary(CallerInfo caller) {
        return customerInsightsService.summary(caller.userId());
    }

    @Operation(summary = "What I ordered and spent in a period, day by day",
            description = "Business days, both included; both default to today; at most 366 days. Spent = what the "
                    + "delivered orders cost")
    @GetMapping("/activity")
    public CustomerOrderActivityResponse getMyActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            CallerInfo caller) {
        return customerInsightsService.activity(caller.userId(), fromDate, toDate);
    }

    @Operation(summary = "The dishes I order most, with today's price and stock",
            description = "Most orders first. available = on the menu and in stock now; a dish no longer on any "
                    + "menu has no price. 503 when the catalog cannot be asked (no price is ever guessed)")
    @GetMapping("/favourite-dishes")
    public List<FavouriteDishResponse> getMyFavouriteDishes(
            @Parameter(description = "How many dishes, 1 to 20") @RequestParam(defaultValue = "6") @Min(1) @Max(20)
            int limit,
            CallerInfo caller) {
        return customerInsightsService.favouriteDishes(caller.userId(), limit);
    }

    @Operation(summary = "Track an order: status and timeline")
    @GetMapping("/{orderId}")
    public OrderResponse getMyOrder(@PathVariable Long orderId, CallerInfo caller) {
        return customerOrderService.getMyOrder(caller.userId(), orderId);
    }

    @Operation(summary = "Cancel my order (only before the restaurant accepts it)")
    @PutMapping("/{orderId}/cancel")
    public OrderResponse cancelMyOrder(@PathVariable Long orderId, @Valid @RequestBody ReasonRequest request,
                                       CallerInfo caller) {
        return customerOrderService.cancelMyOrder(caller.userId(), orderId, request.reason());
    }
}
