package com.fooddelivery.paymentservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the browser needs to open Razorpay Checkout for one order. Contains the public key id only: the key
 * secret never leaves the server.
 */
public record RazorpayCheckoutResponse(

        @Schema(description = "Our payment (PROCESSING while the customer pays in Checkout)", example = "17")
        Long paymentId,

        @Schema(example = "42")
        Long orderId,

        @Schema(description = "Checkout option order_id", example = "order_QaB12cD34eF56g")
        String razorpayOrderId,

        @Schema(description = "Checkout option key: the public key id", example = "rzp_test_1DP5mmOlF5G5ag")
        String keyId,

        @Schema(description = "Checkout option amount, in paise (₹353.95 = 35395)", example = "35395")
        long amount,

        @Schema(example = "INR")
        String currency,

        @Schema(description = "Checkout option name", example = "Front-App")
        String merchantName,

        @Schema(description = "Checkout option description", example = "Order #42")
        String description,

        @Schema(description = "The method chosen at checkout, to preselect in Razorpay: card, upi or wallet",
                example = "upi")
        String preferredMethod
) {
}
