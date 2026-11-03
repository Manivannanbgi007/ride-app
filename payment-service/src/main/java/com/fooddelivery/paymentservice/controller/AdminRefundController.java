package com.fooddelivery.paymentservice.controller;

import com.fooddelivery.paymentservice.dto.request.RefundSearchCriteria;
import com.fooddelivery.paymentservice.dto.response.PageResponse;
import com.fooddelivery.paymentservice.dto.response.RefundResponse;
import com.fooddelivery.paymentservice.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform admins: the refund list. Refunds are started from a payment (POST /admin/payments/{id}/refunds). */
@Tag(name = "Admin - Refunds", description = "Refund requests and their outcome")
@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
public class AdminRefundController {

    private final AdminPaymentService adminPaymentService;

    @Operation(summary = "Search refunds", description = "Failed refunds: ?status=FAILED")
    @GetMapping
    public PageResponse<RefundResponse> searchRefunds(
            @Valid @ParameterObject RefundSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminPaymentService.searchRefunds(criteria, pageable);
    }

    @Operation(summary = "One refund: amount, reason, status, who requested it, when it was requested and completed")
    @GetMapping("/{refundId}")
    public RefundResponse getRefund(@PathVariable Long refundId) {
        return adminPaymentService.getRefund(refundId);
    }
}
