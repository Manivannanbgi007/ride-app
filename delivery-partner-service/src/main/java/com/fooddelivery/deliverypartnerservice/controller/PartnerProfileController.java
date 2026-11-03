package com.fooddelivery.deliverypartnerservice.controller;

import com.fooddelivery.deliverypartnerservice.dto.request.UpdatePartnerProfileRequest;
import com.fooddelivery.deliverypartnerservice.dto.response.PartnerResponse;
import com.fooddelivery.deliverypartnerservice.service.PartnerProfileService;
import com.fooddelivery.deliverypartnerservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling partner's profile. Going online (PUT /api/v1/delivery/partners/me/availability) is delivery-service's
 * API; the API Gateway sends each path to its service.
 */
@Tag(name = "Delivery partner: profile", description = "The calling partner's profile (DELIVERY_PARTNER)")
@RestController
@RequestMapping("/api/v1/delivery/partners/me")
@RequiredArgsConstructor
public class PartnerProfileController {

    private final PartnerProfileService partnerProfileService;

    @Operation(summary = "My partner profile", description = "Created on the first call if the account is a delivery partner")
    @GetMapping
    public PartnerResponse getProfile(CallerInfo caller) {
        return partnerProfileService.getProfile(caller);
    }

    @Operation(summary = "Set my vehicle and city", description = "Required before going online")
    @PutMapping
    public PartnerResponse updateProfile(CallerInfo caller, @Valid @RequestBody UpdatePartnerProfileRequest request) {
        return partnerProfileService.updateProfile(caller, request);
    }
}
