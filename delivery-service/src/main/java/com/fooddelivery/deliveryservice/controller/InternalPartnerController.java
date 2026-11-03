package com.fooddelivery.deliveryservice.controller;

import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.dto.response.RosterPartnerResponse;
import com.fooddelivery.deliveryservice.service.PartnerRosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API (delivery-partner-service). The API Gateway has no route for /api/v1/internal/**. */
@Tag(name = "Internal: delivery partners",
        description = "For delivery-partner-service only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/delivery-partners")
@RequiredArgsConstructor
public class InternalPartnerController {

    private final PartnerRosterService partnerRosterService;

    @Operation(summary = "Every partner on the dispatch list, oldest user id first",
            description = "Read once by delivery-partner-service to take over the partners kept here before it existed")
    @GetMapping
    public PageResponse<RosterPartnerResponse> getRoster(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return partnerRosterService.getRoster(page, size);
    }
}
