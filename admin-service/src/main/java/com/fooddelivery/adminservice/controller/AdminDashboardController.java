package com.fooddelivery.adminservice.controller;

import java.time.LocalDate;

import com.fooddelivery.adminservice.dto.response.DashboardResponse;
import com.fooddelivery.adminservice.dto.response.OrderActivityResponse;
import com.fooddelivery.adminservice.service.DashboardService;
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

/** Requires an access token with the role PLATFORM_ADMIN. */
@Tag(name = "Dashboard", description = "Platform figures and charts, from the services that own the data")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Dashboard: today's figures, totals, and charts for the last days",
            description = "If a service does not answer, its figures are null and it is listed in 'unavailable'.")
    @GetMapping
    public DashboardResponse getDashboard(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
                                          CallerInfo caller) {
        return dashboardService.getDashboard(days, caller);
    }

    @Operation(summary = "Order charts for one day, week, month or any period of up to a year",
            description = "The figures behind 'Chart Order' and 'Order Rates' for fromDate..toDate (calendar days in the "
                    + "business time zone, both included). A last day still to come is cut at today. If a service does "
                    + "not answer, its figures are null and it is listed in 'unavailable'.")
    @GetMapping("/order-activity")
    public OrderActivityResponse getOrderActivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            CallerInfo caller) {
        return dashboardService.getOrderActivity(fromDate, toDate, caller);
    }
}
