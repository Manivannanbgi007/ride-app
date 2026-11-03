package com.fooddelivery.adminservice.controller;

import java.time.LocalDate;

import com.fooddelivery.adminservice.dto.request.RevenueGroupBy;
import com.fooddelivery.adminservice.dto.response.CustomerReportResponse;
import com.fooddelivery.adminservice.dto.response.DeliveryReportResponse;
import com.fooddelivery.adminservice.dto.response.OrderReportResponse;
import com.fooddelivery.adminservice.dto.response.RestaurantReportResponse;
import com.fooddelivery.adminservice.dto.response.RevenueReportResponse;
import com.fooddelivery.adminservice.service.ReportService;
import com.fooddelivery.adminservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform reports. Periods are calendar days in the business time zone; without dates a report covers the last 30
 * days, at most 366. Lists are limited by "limit". Requires an access token with the role PLATFORM_ADMIN.
 */
@Tag(name = "Reports", description = "Orders, revenue, restaurants, customers and deliveries for a period")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "Order report: totals, by status, by date, by restaurant and by delivery city")
    @GetMapping("/orders")
    public OrderReportResponse orders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            CallerInfo caller) {
        return reportService.orders(fromDate, toDate, limit, caller);
    }

    @Operation(summary = "Revenue report: per day, week or month, per restaurant and per payment method")
    @GetMapping("/revenue")
    public RevenueReportResponse revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "DAY") RevenueGroupBy groupBy,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            CallerInfo caller) {
        return reportService.revenue(fromDate, toDate, groupBy, limit, caller);
    }

    @Operation(summary = "Restaurant report: active, new, suspended, top and low-performing restaurants")
    @GetMapping("/restaurants")
    public RestaurantReportResponse restaurants(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            CallerInfo caller) {
        return reportService.restaurants(fromDate, toDate, limit, caller);
    }

    @Operation(summary = "Customer report: new, active and inactive customers, top customers, order frequency")
    @GetMapping("/customers")
    public CustomerReportResponse customers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            CallerInfo caller) {
        return reportService.customers(fromDate, toDate, limit, caller);
    }

    @Operation(summary = "Delivery report: completed and failed deliveries, average delivery time, partner performance")
    @GetMapping("/deliveries")
    public DeliveryReportResponse deliveries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            CallerInfo caller) {
        return reportService.deliveries(fromDate, toDate, limit, caller);
    }
}
