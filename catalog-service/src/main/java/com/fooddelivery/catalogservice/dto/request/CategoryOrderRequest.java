package com.fooddelivery.catalogservice.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CategoryOrderRequest(

        @Schema(description = "Every category id exactly once, in the new display order", example = "[3, 1, 2]")
        @NotEmpty
        List<@NotNull Long> categoryIds
) {
}
