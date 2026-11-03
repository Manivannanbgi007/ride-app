package com.fooddelivery.deliverypartnerservice.controller;

import com.fooddelivery.deliverypartnerservice.dto.request.PartnerSearchCriteria;
import com.fooddelivery.deliverypartnerservice.dto.request.StatusChangeRequest;
import com.fooddelivery.deliverypartnerservice.dto.response.AuditLogResponse;
import com.fooddelivery.deliverypartnerservice.dto.response.PageResponse;
import com.fooddelivery.deliverypartnerservice.dto.response.PartnerResponse;
import com.fooddelivery.deliverypartnerservice.service.AdminPartnerService;
import com.fooddelivery.deliverypartnerservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admins managing partners. Numbers about partners' work (/availability, /stats, /{userId}/deliveries) are
 * delivery-service's API under the same path; the API Gateway sends each path to its service.
 */
@Tag(name = "Admin: delivery partners", description = "Find, deactivate and activate delivery partners (PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/delivery-partners")
@RequiredArgsConstructor
public class AdminPartnerController {

    private final AdminPartnerService adminPartnerService;

    @Operation(summary = "Search partners", description = "Available ones in a city: ?status=AVAILABLE&city=Bengaluru")
    @GetMapping
    public PageResponse<PartnerResponse> search(
            @Valid @ParameterObject PartnerSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return adminPartnerService.search(criteria, pageable);
    }

    @Operation(summary = "One partner")
    @GetMapping("/{userId}")
    public PartnerResponse getPartner(@PathVariable Long userId) {
        return adminPartnerService.getPartner(userId);
    }

    @Operation(summary = "Activate a suspended partner (audited)", description = "The partner starts OFFLINE")
    @PutMapping("/{userId}/activate")
    public PartnerResponse activate(@PathVariable Long userId, @Valid @RequestBody StatusChangeRequest request,
                                    CallerInfo caller) {
        return adminPartnerService.activate(userId, request.reason(), caller);
    }

    @Operation(summary = "Deactivate (suspend) a partner (audited)",
            description = "Not while the partner is on a delivery: reassign it first")
    @PutMapping("/{userId}/deactivate")
    public PartnerResponse deactivate(@PathVariable Long userId, @Valid @RequestBody StatusChangeRequest request,
                                      CallerInfo caller) {
        return adminPartnerService.deactivate(userId, request.reason(), caller);
    }

    @Operation(summary = "Audit history of a partner (newest first)")
    @GetMapping("/{userId}/audit-logs")
    public PageResponse<AuditLogResponse> getAuditLogs(@PathVariable Long userId,
                                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminPartnerService.getAuditLogs(userId, pageable);
    }
}
