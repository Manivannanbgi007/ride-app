package com.fooddelivery.paymentservice.controller;

import com.fooddelivery.paymentservice.dto.request.RazorpayOrderRequest;
import com.fooddelivery.paymentservice.dto.request.RazorpayVerifyRequest;
import com.fooddelivery.paymentservice.dto.response.PaymentResponse;
import com.fooddelivery.paymentservice.dto.response.RazorpayCheckoutResponse;
import com.fooddelivery.paymentservice.dto.response.RazorpayConfigResponse;
import com.fooddelivery.paymentservice.dto.response.RazorpayWebhookResponse;
import com.fooddelivery.paymentservice.service.RazorpayPaymentService;
import com.fooddelivery.paymentservice.service.RazorpayWebhookService;
import com.fooddelivery.paymentservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paying with Razorpay Checkout (when app.payment.provider is RAZORPAY). The customer endpoints require a customer's
 * access token; the webhook is called by Razorpay and is checked by its signature instead.
 */
@Tag(name = "Razorpay", description = "Pay an order with Razorpay Checkout; Razorpay's webhook")
@RestController
@RequestMapping("/api/v1/payments/razorpay")
@RequiredArgsConstructor
public class RazorpayPaymentController {

    private final RazorpayPaymentService razorpayPaymentService;
    private final RazorpayWebhookService razorpayWebhookService;

    @Operation(summary = "Is Razorpay Checkout used here, and with which public key id",
            description = "enabled=false: pay with POST /api/v1/payments (the simulated gateway) instead.")
    @GetMapping("/config")
    public RazorpayConfigResponse config() {
        return razorpayPaymentService.config();
    }

    @Operation(summary = "Start paying an order: creates (or reopens) its Razorpay order",
            description = "The amount is the order's total from order-service, never a value sent by the browser. "
                    + "Open Razorpay Checkout with the answer (key, order_id, amount, currency). Asking again for the "
                    + "same order returns the same Razorpay order, so it can never be paid twice.")
    @PostMapping("/orders")
    public RazorpayCheckoutResponse startCheckout(@Valid @RequestBody RazorpayOrderRequest request, CallerInfo caller) {
        return razorpayPaymentService.startCheckout(caller.userId(), request.orderId());
    }

    @Operation(summary = "Confirm a payment made in Checkout",
            description = "Send the three values Checkout's handler received. The signature is checked, then Razorpay "
                    + "is asked about the payment (order, amount, currency); a held (authorized) payment is captured. "
                    + "status SUCCESS: paid, the order moves on. PROCESSING: not confirmed yet (the webhook finishes "
                    + "it). 422: the signature is wrong, nothing changed.")
    @PostMapping("/verify")
    public PaymentResponse verify(@Valid @RequestBody RazorpayVerifyRequest request, CallerInfo caller) {
        return razorpayPaymentService.confirmPayment(caller.userId(), request.razorpayOrderId(),
                request.razorpayPaymentId(), request.razorpaySignature());
    }

    @Operation(summary = "Razorpay webhook (called by Razorpay, not by the app)",
            description = "Set this URL in the Razorpay Dashboard (Account & Settings -> Webhooks) with the secret "
                    + "from RAZORPAY_WEBHOOK_SECRET and the events payment.authorized, payment.captured, "
                    + "payment.failed and order.paid. 401: wrong signature. A repeated event id answers DUPLICATE.")
    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RazorpayWebhookResponse webhook(
            @Parameter(description = "HMAC-SHA256 of the body with the webhook secret (hex)", required = true)
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @Parameter(description = "Razorpay's id of this event; the same for every retry of it")
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId,
            // The raw text: the signature is computed over exactly these bytes
            @RequestBody String payload) {
        return new RazorpayWebhookResponse(razorpayWebhookService.receive(payload, signature, eventId).name());
    }
}
