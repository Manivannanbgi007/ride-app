package com.fooddelivery.paymentservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** The answer to Razorpay: any 2xx means "received, do not send it again". */
public record RazorpayWebhookResponse(

        @Schema(description = "PROCESSED, DUPLICATE (this event id was handled before) or IGNORED (an event this "
                + "service does not use, or a payment it does not know)", example = "PROCESSED")
        String result
) {
}
