package com.fooddelivery.paymentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayPayment;

/**
 * The part of a Razorpay webhook this service reads, for example:
 * <pre>
 * {"entity": "event", "event": "payment.captured",
 *  "payload": {"payment": {"entity": {"id": "pay_...", "order_id": "order_...", "status": "captured",
 *                                     "amount": 35395, "currency": "INR", "method": "upi", ...}}}}
 * </pre>
 * Everything else in the message is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayWebhookEvent(String event, Payload payload) {

    /** The payment the event is about, or null (events about other things, such as refunds). */
    public RazorpayPayment payment() {
        return payload == null || payload.payment() == null ? null : payload.payment().entity();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(PaymentEntity payment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentEntity(RazorpayPayment entity) {
    }
}
