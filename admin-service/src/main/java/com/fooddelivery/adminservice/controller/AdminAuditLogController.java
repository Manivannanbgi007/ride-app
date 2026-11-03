package com.fooddelivery.adminservice.controller;

import java.util.List;

import com.fooddelivery.adminservice.dto.request.AuditLogSearchCriteria;
import com.fooddelivery.adminservice.dto.request.ReasonRequest;
import com.fooddelivery.adminservice.dto.response.AuditActionCount;
import com.fooddelivery.adminservice.dto.response.AuditBackfillResponse;
import com.fooddelivery.adminservice.dto.response.AuditRecordDetailsResponse;
import com.fooddelivery.adminservice.dto.response.AuditRecordResponse;
import com.fooddelivery.adminservice.dto.response.PageResponse;
import com.fooddelivery.adminservice.service.AuditBackfillService;
import com.fooddelivery.adminservice.service.AuditLogService;
import com.fooddelivery.adminservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The platform-wide audit log: every audited admin action of every service. Read-only (records are immutable).
 * Requires an access token with the role PLATFORM_ADMIN.
 */
@Tag(name = "Audit log", description = "Search every admin action across the platform")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;
    private final AuditBackfillService auditBackfillService;

    @Operation(summary = "Search the audit log (newest first; sort by auditedAt, action, sourceService, adminUserId "
            + "or entityType)")
    @GetMapping
    public PageResponse<AuditRecordResponse> search(@Valid @ParameterObject AuditLogSearchCriteria criteria,
                                                    @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return auditLogService.search(criteria, pageable);
    }

    @Operation(summary = "Which actions the log has, per service, with counts (for filter lists)")
    @GetMapping("/actions")
    public List<AuditActionCount> getActions() {
        return auditLogService.getActions();
    }

    @Operation(summary = "One audit record, with the other records of the same admin request")
    @GetMapping("/{auditId}")
    public AuditRecordDetailsResponse getRecord(@PathVariable Long auditId) {
        return auditLogService.getRecord(auditId);
    }

    @Operation(summary = "Ask every service to send its audit records again (fills in records made before the "
            + "platform-wide log existed; safe to repeat; audited)")
    @PostMapping("/backfill")
    public AuditBackfillResponse backfill(@Valid @RequestBody ReasonRequest request, CallerInfo caller) {
        return auditBackfillService.backfill(caller, request.reason());
    }
}
