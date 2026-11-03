package com.fooddelivery.catalogservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeCategoryRequest(

        @NotNull
        Long categoryId,

        @Schema(description = "Why the admin moves the item; stored in the audit log",
                example = "Listed under the wrong category")
        @NotBlank @Size(max = 500)
        String reason
) {
}
