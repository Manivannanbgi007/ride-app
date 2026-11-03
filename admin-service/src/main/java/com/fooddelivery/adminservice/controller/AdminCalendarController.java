package com.fooddelivery.adminservice.controller;

import java.time.LocalDate;
import java.util.List;

import com.fooddelivery.adminservice.dto.request.CalendarEventType;
import com.fooddelivery.adminservice.dto.response.CalendarResponse;
import com.fooddelivery.adminservice.service.CalendarService;
import com.fooddelivery.adminservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requires an access token with the role PLATFORM_ADMIN. */
@Tag(name = "Calendar", description = "Orders, coupons and new restaurants on a calendar, from the services that own the data")
@RestController
@RequestMapping("/api/v1/admin/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final CalendarService calendarService;

    @Operation(summary = "Calendar events for a range of up to 400 days",
            description = "Read-only, made from real data: orders per day (placed, delivered, cancelled), coupons "
                    + "over their validity dates, restaurants registered per day. 'types' (ORDERS, COUPONS, "
                    + "RESTAURANTS, comma separated) defaults to all. If a source does not answer it is listed in "
                    + "'unavailable' and the rest is returned; if none answers the result is 503.")
    @GetMapping
    public CalendarResponse getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<CalendarEventType> types,
            CallerInfo caller) {
        return calendarService.getCalendar(from, to, types, caller);
    }
}
