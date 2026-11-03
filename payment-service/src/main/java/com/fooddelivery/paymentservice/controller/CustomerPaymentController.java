package com.fooddelivery.paymentservice.controller;

import java.net.URI;
import java.util.List;

import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.dto.response.PaymentResponse;
import com.fooddelivery.paymentservice.service.PaymentService;
import com.fooddelivery.paymentservice.service.PaymentService.PaymentResult;
import com.fooddelivery.paymentservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customers paying their orders online. Requires an access token with the role CUSTOMER. */
@Tag(name = "Payments", description = "Pay an order online (idempotent)")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Pay an order that is waiting for payment",
            description = "The answer contains the result (SUCCESS or FAILED). Retrying with the same Idempotency-Key "
                    + "returns the same payment (200 + Idempotent-Replayed: true) and never charges twice.")
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @Parameter(description = "Unique key for this payment attempt (8-64 letters, digits, - or _)", required = true)
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String idempotencyKey,
            @Valid @RequestBody PayRequest request,
            CallerInfo caller) {
        PaymentResult result = paymentService.pay(caller.userId(), idempotencyKey, request);

        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.payment());
        }
        return ResponseEntity.created(URI.create("/api/v1/payments/" + result.payment().id())).body(result.payment());
    }

    @Operation(summary = "My payments (optionally for one order), newest first")
    @GetMapping
    public List<PaymentResponse> getMyPayments(@RequestParam(required = false) Long orderId, CallerInfo caller) {
        return paymentService.getMyPayments(caller.userId(), orderId);
    }

    @Operation(summary = "One of my payments")
    @GetMapping("/{paymentId}")
    public PaymentResponse getMyPayment(@PathVariable Long paymentId, CallerInfo caller) {
        return paymentService.getMyPayment(caller.userId(), paymentId);
    }
}
