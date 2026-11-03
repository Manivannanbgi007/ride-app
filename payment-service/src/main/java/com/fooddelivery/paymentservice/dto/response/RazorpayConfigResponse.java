package com.fooddelivery.paymentservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whether online payments go through Razorpay Checkout here, and the public key id to open it with. */
public record RazorpayConfigResponse(

        @Schema(description = "true: pay with Razorpay Checkout; false: the built-in simulated gateway "
                + "(POST /api/v1/payments)")
        boolean enabled,

        @Schema(description = "The public key id (null when Razorpay is off)", example = "rzp_test_1DP5mmOlF5G5ag")
        String keyId,

        @Schema(description = "true for Test Mode keys (rzp_test_...): no real money moves", example = "true")
        boolean testMode
) {
}
