package com.fooddelivery.deliverypartnerservice.controller;

import com.fooddelivery.deliverypartnerservice.dto.response.InternalPartnerResponse;
import com.fooddelivery.deliverypartnerservice.dto.response.PartnerImportResponse;
import com.fooddelivery.deliverypartnerservice.service.LegacyPartnerImportService;
import com.fooddelivery.deliverypartnerservice.service.PartnerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API. The API Gateway has no route for /api/v1/internal/**. */
@Tag(name = "Internal: delivery partners",
        description = "For delivery-service and operators; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/delivery-partners")
@RequiredArgsConstructor
public class InternalPartnerController {

    private final PartnerProfileService partnerProfileService;
    private final LegacyPartnerImportService legacyPartnerImportService;

    @Operation(summary = "A partner's profile and account status (delivery-service)",
            description = "Registers the partner first if needed; 404 if the user is not a delivery partner")
    @GetMapping("/{userId}")
    public InternalPartnerResponse getPartner(@PathVariable Long userId) {
        return partnerProfileService.getForDelivery(userId);
    }

    @Operation(summary = "Take over the partners delivery-service kept before this service existed (safe to repeat)")
    @PostMapping("/import")
    public PartnerImportResponse importFromDeliveryService() {
        return legacyPartnerImportService.importFromDeliveryService();
    }
}
