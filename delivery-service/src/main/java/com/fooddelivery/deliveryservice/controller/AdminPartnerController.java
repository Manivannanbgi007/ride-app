package com.fooddelivery.deliveryservice.controller;

import java.time.LocalDate;

import com.fooddelivery.deliveryservice.dto.response.DeliverySummaryResponse;
import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.dto.response.PartnerAvailabilityResponse;
import com.fooddelivery.deliveryservice.dto.response.PartnerStatsResponse;
import com.fooddelivery.deliveryservice.service.AdminPartnerService;
import com.fooddelivery.deliveryservice.service.PartnerStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What partners are doing, for admins. Finding, suspending and reactivating partners (/, /{userId}, /activate,
 * /deactivate, /audit-logs) is delivery-partner-service's API under the same path; the API Gateway sends each path
 * to its service.
 */
@Tag(name = "Admin: delivery partners at work", description = "Partners online and their deliveries (PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/delivery-partners")
@RequiredArgsConstructor
public class AdminPartnerController {

    private final AdminPartnerService adminPartnerService;
    private final PartnerStatsService partnerStatsService;

    @Operation(summary = "How many partners are available, busy, offline and suspended right now")
    @GetMapping("/availability")
    public PartnerAvailabilityResponse getAvailability() {
        return adminPartnerService.getAvailability();
    }

    @Operation(summary = "Partners online now, deliveries waiting for a partner, and the top partners of a period "
            + "(default: today)")
    @GetMapping("/stats")
    public PartnerStatsResponse getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return partnerStatsService.getStats(fromDate, toDate, limit);
    }

    @Operation(summary = "A partner's deliveries (newest first)")
    @GetMapping("/{userId}/deliveries")
    public PageResponse<DeliverySummaryResponse> getDeliveries(
            @PathVariable Long userId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminPartnerService.getDeliveries(userId, pageable);
    }
}
