package com.fooddelivery.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CouponCodeRequest(

        @Schema(description = "The coupon code (not case-sensitive)", example = "WELCOME100")
        @NotBlank @Pattern(regexp = "\\s*[A-Za-z0-9_-]{3,30}\\s*", message = "3 to 30 letters, digits, - or _")
        String code
) {
}
