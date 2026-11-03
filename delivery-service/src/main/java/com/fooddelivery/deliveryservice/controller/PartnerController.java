package com.fooddelivery.deliveryservice.controller;

import com.fooddelivery.deliveryservice.dto.request.ChangeAvailabilityRequest;
import com.fooddelivery.deliveryservice.dto.response.PartnerResponse;
import com.fooddelivery.deliveryservice.service.PartnerService;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling partner's availability. The profile (GET/PUT /api/v1/delivery/partners/me) is
 * delivery-partner-service's API; the API Gateway sends each path to its service.
 */
@Tag(name = "Delivery partner: availability", description = "The calling partner goes online or offline (DELIVERY_PARTNER)")
@RestController
@RequestMapping("/api/v1/delivery/partners/me")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @Operation(summary = "Go online (AVAILABLE) or OFFLINE",
            description = "Not possible while on a delivery (BUSY) or when suspended; vehicle and city must be set")
    @PutMapping("/availability")
    public PartnerResponse changeAvailability(CallerInfo caller, @Valid @RequestBody ChangeAvailabilityRequest request) {
        return partnerService.changeAvailability(caller, request);
    }
}
