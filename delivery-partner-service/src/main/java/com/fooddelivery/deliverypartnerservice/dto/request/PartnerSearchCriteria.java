package com.fooddelivery.deliverypartnerservice.dto.request;

import com.fooddelivery.deliverypartnerservice.entity.PartnerStatus;
import io.swagger.v3.oas.annotations.Parameter;

/** Admin partner search filters; all optional. */
public record PartnerSearchCriteria(

        @Parameter(description = "OFFLINE, AVAILABLE, BUSY or SUSPENDED")
        PartnerStatus status,

        @Parameter(description = "Partners working in this city", example = "Bengaluru")
        String city,

        @Parameter(description = "Part of the name or phone number", example = "ravi")
        String q
) {
}
