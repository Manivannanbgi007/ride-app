package com.fooddelivery.notificationservice.controller;

import java.time.LocalDate;

import com.fooddelivery.notificationservice.dto.request.NotificationSearchCriteria;
import com.fooddelivery.notificationservice.dto.request.ResendRequest;
import com.fooddelivery.notificationservice.dto.response.AdminNotificationResponse;
import com.fooddelivery.notificationservice.dto.response.AuditLogResponse;
import com.fooddelivery.notificationservice.dto.response.NotificationSummaryResponse;
import com.fooddelivery.notificationservice.dto.response.PageResponse;
import com.fooddelivery.notificationservice.service.AdminNotificationService;
import com.fooddelivery.notificationservice.web.CallerInfo;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: notifications", description = "Monitor notifications and resend failed ones (PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @Operation(summary = "Search notifications", description = "Failed ones: ?status=FAILED   Of one order: ?orderId=42")
    @GetMapping
    public PageResponse<AdminNotificationResponse> search(
            @Valid @ParameterObject NotificationSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminNotificationService.search(criteria, pageable);
    }

    @Operation(summary = "Totals per status and per channel for a period (default: today)")
    @GetMapping("/summary")
    public NotificationSummaryResponse getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return adminNotificationService.getSummary(fromDate, toDate);
    }

    @Operation(summary = "One notification with its delivery details (attempts, last error, address used)")
    @GetMapping("/{notificationId}")
    public AdminNotificationResponse getNotification(@PathVariable Long notificationId) {
        return adminNotificationService.getNotification(notificationId);
    }

    @Operation(summary = "Send a FAILED or SKIPPED notification again (audited)")
    @PostMapping("/{notificationId}/resend")
    public AdminNotificationResponse resend(@PathVariable Long notificationId,
                                            @Valid @RequestBody ResendRequest request,
                                            CallerInfo caller) {
        return adminNotificationService.resend(notificationId, request.reason(), caller);
    }

    @Operation(summary = "Audit history of a notification (newest first)")
    @GetMapping("/{notificationId}/audit-logs")
    public PageResponse<AuditLogResponse> getAuditLogs(@PathVariable Long notificationId,
                                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminNotificationService.getAuditLogs(notificationId, pageable);
    }
}
