package com.fooddelivery.orderservice.controller;

import java.time.LocalDate;

import com.fooddelivery.orderservice.dto.response.CalendarStatsResponse;
import com.fooddelivery.orderservice.dto.response.CustomerOrderStatsResponse;
import com.fooddelivery.orderservice.dto.response.CustomerTopFoodsResponse;
import com.fooddelivery.orderservice.dto.response.LocationStatsResponse;
import com.fooddelivery.orderservice.dto.response.MenuItemRevenueResponse;
import com.fooddelivery.orderservice.dto.response.OrderStatsResponse;
import com.fooddelivery.orderservice.dto.response.RestaurantPerformanceResponse;
import com.fooddelivery.orderservice.service.CustomerTopFoodsService;
import com.fooddelivery.orderservice.service.MenuItemRevenueService;
import com.fooddelivery.orderservice.service.OrderStatsService;
import com.fooddelivery.orderservice.service.RevenuePeriod;
import com.fooddelivery.orderservice.service.TopFoodsPeriod;
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
 * Order statistics for the admin dashboard and reports (admin-service combines them with other services' numbers).
 * Periods are calendar days in the business time zone, both dates default to today, at most 366 days.
 * Requires an access token with the role PLATFORM_ADMIN.
 */
@Tag(name = "Admin: order statistics", description = "Orders, revenue, restaurants, customers and cities")
@RestController
@RequestMapping("/api/v1/admin/orders/stats")
@RequiredArgsConstructor
public class AdminOrderStatsController {

    private final OrderStatsService orderStatsService;
    private final MenuItemRevenueService menuItemRevenueService;
    private final CustomerTopFoodsService customerTopFoodsService;

    @Operation(summary = "Orders and revenue in a period: per status, per payment method and per day; plus overall totals")
    @GetMapping
    public OrderStatsResponse getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return orderStatsService.getStats(fromDate, toDate);
    }

    @Operation(summary = "Orders placed, delivered and cancelled per day, for the admin calendar (up to 400 days)",
            description = "Only days with orders. Both dates are required: calendar days in the business time zone.")
    @GetMapping("/calendar")
    public CalendarStatsResponse getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return orderStatsService.getCalendar(from, to);
    }

    @Operation(summary = "Restaurant performance: top and lowest by revenue, most cancellations")
    @GetMapping("/restaurants")
    public RestaurantPerformanceResponse getRestaurantPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return orderStatsService.getRestaurantPerformance(fromDate, toDate, limit);
    }

    @Operation(summary = "Customers' ordering: how many ordered, how often, and the top customers by spend")
    @GetMapping("/customers")
    public CustomerOrderStatsResponse getCustomerStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return orderStatsService.getCustomerStats(fromDate, toDate, limit);
    }

    @Operation(summary = "Revenue of one dish over time (delivered orders): last 12 months, 12 weeks or 30 days, no gaps")
    @GetMapping("/menu-items/{menuItemId}/revenue")
    public MenuItemRevenueResponse getMenuItemRevenue(@PathVariable long menuItemId,
            @RequestParam(defaultValue = "MONTHLY") RevenuePeriod period) {
        return menuItemRevenueService.getRevenue(menuItemId, period);
    }

    @Operation(summary = "The dishes one customer ordered most: last 30 days, last 7 days or today")
    @GetMapping("/customers/{customerId}/top-foods")
    public CustomerTopFoodsResponse getCustomerTopFoods(@PathVariable long customerId,
            @RequestParam(defaultValue = "MONTHLY") TopFoodsPeriod period,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return customerTopFoodsService.getTopFoods(customerId, period, limit);
    }

    @Operation(summary = "Orders per delivery city, most orders first")
    @GetMapping("/locations")
    public LocationStatsResponse getLocationStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return orderStatsService.getLocationStats(fromDate, toDate, limit);
    }
}
