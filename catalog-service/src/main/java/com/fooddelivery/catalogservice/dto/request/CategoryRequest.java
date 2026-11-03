package com.fooddelivery.catalogservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CategoryRequest(

        @Schema(example = "Rolls & Wraps")
        @NotBlank @Size(max = 60)
        String name,

        @Size(max = 255)
        String description,

        @URL @Size(max = 500)
        String imageUrl
) {
}
