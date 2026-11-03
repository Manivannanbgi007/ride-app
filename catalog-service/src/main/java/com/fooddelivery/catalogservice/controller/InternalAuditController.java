package com.fooddelivery.catalogservice.controller;

import com.fooddelivery.catalogservice.dto.response.AuditReplayResponse;
import com.fooddelivery.catalogservice.service.AdminAuditReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API (admin-service). The API Gateway has no route for /api/v1/internal/**. */
@Tag(name = "Internal: audit", description = "For admin-service only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/audit-logs")
public class InternalAuditController {

    private final AdminAuditReplayService adminAuditReplayService;
    private final String serviceName;

    public InternalAuditController(AdminAuditReplayService adminAuditReplayService,
                                   @Value("${spring.application.name}") String serviceName) {
        this.adminAuditReplayService = adminAuditReplayService;
        this.serviceName = serviceName;
    }

    @Operation(summary = "Send every audit record again to the platform-wide audit log (safe to repeat)")
    @PostMapping("/events/republish")
    public AuditReplayResponse republish() {
        return new AuditReplayResponse(serviceName, adminAuditReplayService.republishAll());
    }
}
