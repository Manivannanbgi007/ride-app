package com.fooddelivery.paymentservice.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.paymentservice.config.AppProperties;
import com.fooddelivery.paymentservice.dto.request.PaymentSearchCriteria;
import com.fooddelivery.paymentservice.dto.request.RefundSearchCriteria;
import com.fooddelivery.paymentservice.dto.response.AuditLogResponse;
import com.fooddelivery.paymentservice.dto.response.PageResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentDetailsResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentSummaryResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentSummaryResponse.Totals;
import com.fooddelivery.paymentservice.dto.response.RefundResponse;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import com.fooddelivery.paymentservice.entity.Refund;
import com.fooddelivery.paymentservice.exception.ResourceNotFoundException;
import com.fooddelivery.paymentservice.mapper.PaymentMapper;
import com.fooddelivery.paymentservice.repository.OutboxEventRepository;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.fooddelivery.paymentservice.repository.PaymentSpecifications;
import com.fooddelivery.paymentservice.repository.PaymentTotals;
import com.fooddelivery.paymentservice.repository.RefundRepository;
import com.fooddelivery.paymentservice.validation.SortValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform admins: visibility into payments and refunds. Payment Service stays the owner of this data. */
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private static final String STATUS = "status";
    private static final List<String> SORTABLE_PAYMENT_FIELDS = List.of("createdAt", "completedAt", "amount", STATUS);
    private static final List<String> SORTABLE_REFUND_FIELDS = List.of("requestedAt", "completedAt", "amount", STATUS);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentMapper paymentMapper;
    private final AdminAuditService adminAuditService;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> searchPayments(PaymentSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_PAYMENT_FIELDS);
        BusinessDateRange range = BusinessDateRange.of(criteria.fromDate(), criteria.toDate(), businessZone());

        List<Specification<Payment>> conditions = new ArrayList<>();
        addIfPresent(conditions, "id", criteria.paymentId());
        addIfPresent(conditions, "orderId", criteria.orderId());
        addIfPresent(conditions, "customerId", criteria.customerId());
        addIfPresent(conditions, STATUS, criteria.status());
        addIfPresent(conditions, "method", criteria.method());
        addRange(conditions, "createdAt", range);

        return PageResponse.from(paymentRepository.findAll(Specification.allOf(conditions), pageable)
                .map(paymentMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentDetailsResponse getPaymentDetails(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        return paymentMapper.toDetails(payment,
                refundRepository.findByPaymentIdOrderByRequestedAtDesc(paymentId),
                outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("PAYMENT", paymentId));
    }

    /** Totals per status and per method for a period (default: today). */
    @Transactional(readOnly = true)
    public PaymentSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(clock.withZone(businessZone()));
        LocalDate from = fromDate != null ? fromDate : today;
        LocalDate to = toDate != null ? toDate : today;
        BusinessDateRange range = BusinessDateRange.of(from, to, businessZone());

        Map<PaymentStatus, Totals> byStatus = new EnumMap<>(PaymentStatus.class);
        Map<PaymentMethod, Totals> byMethod = new EnumMap<>(PaymentMethod.class);
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;

        for (PaymentTotals totals : paymentRepository.sumByStatusAndMethod(range.from(), range.before())) {
            Totals row = new Totals(totals.getPaymentCount(), totals.getTotalAmount());
            byStatus.merge(totals.getStatus(), row, AdminPaymentService::add);
            byMethod.merge(totals.getMethod(), row, AdminPaymentService::add);
            if (totals.getStatus() == PaymentStatus.SUCCESS || totals.getStatus() == PaymentStatus.REFUNDED) {
                collected = collected.add(totals.getTotalAmount());
                refunded = refunded.add(totals.getRefundedAmount());
            }
        }

        return new PaymentSummaryResponse(from, to, byStatus, byMethod, collected.setScale(2), refunded.setScale(2), "INR");
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> searchRefunds(RefundSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_REFUND_FIELDS);
        BusinessDateRange range = BusinessDateRange.of(criteria.fromDate(), criteria.toDate(), businessZone());

        List<Specification<Refund>> conditions = new ArrayList<>();
        addIfPresent(conditions, "paymentId", criteria.paymentId());
        addIfPresent(conditions, "orderId", criteria.orderId());
        addIfPresent(conditions, STATUS, criteria.status());
        addRange(conditions, "requestedAt", range);

        return PageResponse.from(refundRepository.findAll(Specification.allOf(conditions), pageable)
                .map(paymentMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(Long refundId) {
        return refundRepository.findById(refundId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getPaymentAuditLogs(Long paymentId, Pageable pageable) {
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_PAYMENT, paymentId, pageable);
    }

    private static <T> void addIfPresent(List<Specification<T>> conditions, String field, Object value) {
        if (value != null) {
            conditions.add(PaymentSpecifications.fieldEquals(field, value));
        }
    }

    private static <T> void addRange(List<Specification<T>> conditions, String field, BusinessDateRange range) {
        if (range.from() != null) {
            conditions.add(PaymentSpecifications.atOrAfter(field, range.from()));
        }
        if (range.before() != null) {
            conditions.add(PaymentSpecifications.before(field, range.before()));
        }
    }

    private static Totals add(Totals first, Totals second) {
        return new Totals(first.count() + second.count(), first.amount().add(second.amount()));
    }

    private ZoneId businessZone() {
        return ZoneId.of(appProperties.businessTimeZone());
    }
}
