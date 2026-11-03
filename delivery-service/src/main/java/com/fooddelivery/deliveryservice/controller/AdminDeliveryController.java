package com.fooddelivery.deliveryservice.controller;

import java.time.LocalDate;

import com.fooddelivery.deliveryservice.dto.request.DeliverySearchCriteria;
import com.fooddelivery.deliveryservice.dto.request.ReassignDeliveryRequest;
import com.fooddelivery.deliveryservice.dto.response.AuditLogResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliveryReportResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliveryResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliverySummaryResponse;
import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.service.AdminDeliveryService;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "Admin: deliveries", description = "Monitor deliveries, find delayed ones and reassign them (PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/deliveries")
@RequiredArgsConstructor
public class AdminDeliveryController {

    private final AdminDeliveryService adminDeliveryService;

    @Operation(summary = "Search deliveries", description = "Assigned ones: ?status=ASSIGNED   Of one order: ?orderId=42")
    @GetMapping
    public PageResponse<DeliverySummaryResponse> search(
            @Valid @ParameterObject DeliverySearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminDeliveryService.search(criteria, pageable);
    }

    @Operation(summary = "Delayed deliveries (longest waiting first)",
            description = "No partner 10 minutes after the food was ready, or not delivered after 45 minutes")
    @GetMapping("/delayed")
    public PageResponse<DeliverySummaryResponse> getDelayed(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminDeliveryService.getDelayed(pageable);
    }

    @Operation(summary = "Delivery report for a period (default: today)",
            description = "Totals per status, average delivery time and the number of delayed deliveries now")
    @GetMapping("/summary")
    public DeliveryReportResponse getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return adminDeliveryService.getReport(fromDate, toDate);
    }

    @Operation(summary = "One delivery with its full history")
    @GetMapping("/{deliveryId}")
    public DeliveryResponse getDelivery(@PathVariable Long deliveryId) {
        return adminDeliveryService.getDelivery(deliveryId);
    }

    @Operation(summary = "Reassign a delivery that is not picked up yet (audited)",
            description = "To a given AVAILABLE partner, or without partnerUserId back to the queue")
    @PutMapping("/{deliveryId}/reassign")
    public DeliveryResponse reassign(@PathVariable Long deliveryId, @Valid @RequestBody ReassignDeliveryRequest request,
                                     CallerInfo caller) {
        return adminDeliveryService.reassign(deliveryId, request, caller);
    }

    @Operation(summary = "Audit history of a delivery (newest first)")
    @GetMapping("/{deliveryId}/audit-logs")
    public PageResponse<AuditLogResponse> getAuditLogs(@PathVariable Long deliveryId,
                                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminDeliveryService.getAuditLogs(deliveryId, pageable);
    }
}
