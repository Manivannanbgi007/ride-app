package com.fooddelivery.paymentservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What Razorpay Checkout hands the browser after a payment (its "handler" callback), passed on unchanged.
 * Not proof of anything by itself: the server checks the signature and asks Razorpay about the payment.
 */
public record RazorpayVerifyRequest(

        @Schema(description = "razorpay_order_id from Checkout", example = "order_QaB12cD34eF56g")
        @NotBlank @Size(max = 64) @Pattern(regexp = "order_[A-Za-z0-9]+")
        String razorpayOrderId,

        @Schema(description = "razorpay_payment_id from Checkout", example = "pay_QaB98zY76xW54v")
        @NotBlank @Size(max = 64) @Pattern(regexp = "pay_[A-Za-z0-9]+")
        String razorpayPaymentId,

        @Schema(description = "razorpay_signature from Checkout (HMAC-SHA256, hex)")
        @NotBlank @Size(max = 128) @Pattern(regexp = "[0-9a-fA-F]+")
        String razorpaySignature
) {
}
