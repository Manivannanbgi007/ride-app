package com.fooddelivery.restaurantservice.dto.request;

import com.fooddelivery.restaurantservice.entity.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record DocumentRequest(

        @NotNull
        DocumentType type,

        @Schema(description = "Link to the uploaded file in document storage",
                example = "https://docs.example.com/restaurants/spice-hub/fssai.pdf")
        @NotBlank @URL @Size(max = 500)
        String url
) {
}
