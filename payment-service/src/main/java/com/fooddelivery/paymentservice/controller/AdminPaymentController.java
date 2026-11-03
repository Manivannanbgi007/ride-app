package com.fooddelivery.paymentservice.controller;

import java.net.URI;
import java.time.LocalDate;

import com.fooddelivery.paymentservice.dto.request.PaymentSearchCriteria;
import com.fooddelivery.paymentservice.dto.request.RefundRequest;
import com.fooddelivery.paymentservice.dto.response.AuditLogResponse;
import com.fooddelivery.paymentservice.dto.response.PageResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentDetailsResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentSummaryResponse;
import com.fooddelivery.paymentservice.dto.response.RefundResponse;
import com.fooddelivery.paymentservice.service.AdminPaymentService;
import com.fooddelivery.paymentservice.service.RefundService;
import com.fooddelivery.paymentservice.service.RefundService.RefundResult;
import com.fooddelivery.paymentservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform admins: payment monitoring and refunds. Requires an access token with the role PLATFORM_ADMIN. */
@Tag(name = "Admin - Payments", description = "Payment monitoring and refunds")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;
    private final RefundService refundService;

    @Operation(summary = "Search payments",
            description = "Failed payment monitoring: ?status=FAILED&sort=createdAt,desc   By order: ?orderId=42")
    @GetMapping
    public PageResponse<PaymentResponse> searchPayments(
            @Valid @ParameterObject PaymentSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminPaymentService.searchPayments(criteria, pageable);
    }

    @Operation(summary = "Totals per status and per method for a period (default: today)")
    @GetMapping("/summary")
    public PaymentSummaryResponse getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return adminPaymentService.getSummary(fromDate, toDate);
    }

    @Operation(summary = "Payment details with its refunds and the messages sent to order-service")
    @GetMapping("/{paymentId}")
    public PaymentDetailsResponse getPayment(@PathVariable Long paymentId) {
        return adminPaymentService.getPaymentDetails(paymentId);
    }

    @Operation(summary = "Refund a payment (full or partial; the order must be cancelled, rejected or delivered)",
            description = "Send an Idempotency-Key: retrying with the same key returns the same refund.")
    @PostMapping("/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> refund(
            @PathVariable Long paymentId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String idempotencyKey,
            @Valid @RequestBody RefundRequest request,
            CallerInfo caller) {
        RefundResult result = refundService.requestRefund(paymentId, request, idempotencyKey, caller);

        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.refund());
        }
        return ResponseEntity.created(URI.create("/api/v1/admin/refunds/" + result.refund().id())).body(result.refund());
    }

    @Operation(summary = "Audit history of a payment (newest first)")
    @GetMapping("/{paymentId}/audit-logs")
    public PageResponse<AuditLogResponse> getAuditLogs(@PathVariable Long paymentId,
                                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminPaymentService.getPaymentAuditLogs(paymentId, pageable);
    }
}
