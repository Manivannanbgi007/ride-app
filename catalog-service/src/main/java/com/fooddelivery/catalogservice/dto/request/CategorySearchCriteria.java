package com.fooddelivery.catalogservice.dto.request;

import com.fooddelivery.catalogservice.entity.CategoryStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Size;

public record CategorySearchCriteria(

        @Parameter(description = "Name contains (case-insensitive)")
        @Size(max = 60)
        String search,

        @Parameter(description = "Filter by status")
        CategoryStatus status
) {
}
