package com.fooddelivery.paymentservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.paymentservice.client.OrderServiceClient;
import com.fooddelivery.paymentservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.paymentservice.client.RazorpayClient;
import com.fooddelivery.paymentservice.dto.request.RefundRequest;
import com.fooddelivery.paymentservice.dto.response.RefundResponse;
import com.fooddelivery.paymentservice.entity.AuditAction;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
import com.fooddelivery.paymentservice.entity.PaymentEnums.RefundStatus;
import com.fooddelivery.paymentservice.entity.PaymentEnums.RequesterType;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import com.fooddelivery.paymentservice.entity.Refund;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import com.fooddelivery.paymentservice.exception.ConflictException;
import com.fooddelivery.paymentservice.exception.ResourceNotFoundException;
import com.fooddelivery.paymentservice.kafka.producer.PaymentEventPublisher;
import com.fooddelivery.paymentservice.mapper.PaymentMapper;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.fooddelivery.paymentservice.repository.RefundRepository;
import com.fooddelivery.paymentservice.strategy.PaymentProviderGuard;
import com.fooddelivery.paymentservice.strategy.PaymentStrategyFactory;
import com.fooddelivery.paymentservice.strategy.ProviderResult;
import com.fooddelivery.paymentservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refunds as a controlled workflow: REQUESTED -> PROCESSING -> SUCCESS or FAILED.
 * <ul>
 *   <li>Admins refund payments of orders that were cancelled, rejected, or delivered with a problem
 *       (full or partial amount, reason required, audited, Idempotency-Key).</li>
 *   <li>The system refunds automatically (saga compensation) when a paid order is cancelled, or when a payment
 *       arrives for an order that no longer accepts it. At most once per payment (fixed idempotency key).</li>
 * </ul>
 * The refund amount is reserved on the payment before calling the provider, so two refunds at the same
 * time can never return more than was paid (the payment's @Version catches the race). A full refund
 * publishes PaymentStatusChanged(REFUNDED) through the transactional outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    /** Orders in these states may be refunded; running orders must be cancelled first. */
    private static final Set<String> REFUNDABLE_ORDER_STATUSES = Set.of("CANCELLED", "REJECTED", "DELIVERED");

    private static final String AUTOMATIC_REFUND_KEY_PREFIX = "auto-refund-";
    private static final int RECOVERY_BATCH_SIZE = 50;

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderServiceClient orderServiceClient;
    private final PaymentStrategyFactory paymentStrategyFactory;
    private final RazorpayClient razorpayClient;
    private final PaymentProviderGuard paymentProviderGuard;
    private final PaymentMetrics paymentMetrics;
    private final PaymentEventPublisher paymentEventPublisher;
    private final AdminAuditService adminAuditService;
    private final PaymentMapper paymentMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public record RefundResult(RefundResponse refund, boolean replayed) {
    }

    public RefundResult requestRefund(Long paymentId, RefundRequest request, String idempotencyKey, CallerInfo admin) {
        Optional<Refund> existing = refundRepository.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey);
        if (existing.isPresent()) {
            return new RefundResult(paymentMapper.toResponse(existing.get()), true);
        }

        Payment payment = findPayment(paymentId);
        OrderSummary order = orderServiceClient.findOrder(payment.getOrderId())
                .orElseThrow(() -> new ConflictException("Order " + payment.getOrderId() + " not found in order-service"));
        if (!REFUNDABLE_ORDER_STATUSES.contains(order.status())) {
            throw new ConflictException("Order " + order.id() + " is " + order.status()
                    + ". Only cancelled, rejected or delivered orders can be refunded: cancel the order first.");
        }

        Long refundId;
        try {
            refundId = transactionTemplate.execute(transaction -> createRefund(paymentId, request.amount(),
                    request.reason(), RequesterType.ADMIN, admin, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            return refundRepository.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey)
                    .map(refund -> new RefundResult(paymentMapper.toResponse(refund), true))
                    .orElseThrow(() -> e);
        }
        return new RefundResult(processRefund(refundId), false);
    }

    /**
     * Saga compensation: asks for a full refund of whatever is left on the payment. Runs in the caller's
     * transaction, so the refund request is saved together with what caused it (for example the id of the
     * processed event); call {@link #processRefund} after the commit to contact the provider.
     *
     * @return the new refund's id, or empty when there is nothing (more) to refund automatically
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> requestAutomaticRefund(Long paymentId, String reason) {
        String key = AUTOMATIC_REFUND_KEY_PREFIX + paymentId;
        if (refundRepository.findByPaymentIdAndIdempotencyKey(paymentId, key).isPresent()) {
            log.info("Automatic refund already exists. paymentId={}", paymentId);
            return Optional.empty();
        }
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCESS || payment.refundableAmount().signum() <= 0) {
            log.info("Nothing to refund automatically. paymentId={} status={}", paymentId,
                    payment == null ? "unknown" : payment.getStatus());
            return Optional.empty();
        }
        return Optional.of(createRefund(paymentId, null, reason, RequesterType.SYSTEM, null, key));
    }

    /** Sends a REQUESTED refund to the provider and records the result. Does nothing for refunds already sent. */
    @SuppressWarnings("checkstyle:IllegalCatch") // any provider failure is a declined refund
    public RefundResponse processRefund(Long refundId) {
        record ProviderCall(PaymentProvider provider, PaymentMethod method, String providerReference, BigDecimal amount) {
        }

        ProviderCall call = transactionTemplate.execute(transaction -> {
            Refund refund = findRefund(refundId);
            if (refund.getStatus() != RefundStatus.REQUESTED) {
                return null; // another thread (or the recovery job) has it already
            }
            refund.setStatus(RefundStatus.PROCESSING);
            Payment payment = findPayment(refund.getPaymentId());
            return new ProviderCall(payment.getProvider(), payment.getMethod(), payment.getProviderReference(),
                    refund.getAmount());
        });
        if (call == null) {
            return paymentMapper.toResponse(findRefund(refundId));
        }

        ProviderResult result;
        try {
            // The money goes back the way it came: a Razorpay payment is refunded through Razorpay
            result = paymentProviderGuard.refund(() -> call.provider() == PaymentProvider.RAZORPAY
                    ? ProviderResult.succeeded(razorpayClient.refundPayment(call.providerReference(),
                            RazorpayClient.toPaise(call.amount())).id())
                    : paymentStrategyFactory.getStrategy(call.method()).refund(call.providerReference(), call.amount()));
        } catch (RuntimeException e) {
            log.error("Refund provider error. refundId={}", refundId, e);
            result = ProviderResult.declined("Payment provider error");
        }

        ProviderResult providerResult = result;
        transactionTemplate.executeWithoutResult(transaction -> recordRefundResult(refundId, providerResult));
        paymentMetrics.refundCompleted(providerResult.success());
        return paymentMapper.toResponse(findRefund(refundId));
    }

    /**
     * Recovery: refunds that were saved but never sent to the provider, because the service stopped in
     * between. Refunds stuck in PROCESSING are not retried blindly: the provider may have paid them out;
     * they show up in the admin refund list for a person to check.
     *
     * @return how many refunds were sent
     */
    public int processStuckRefunds(Duration requestedLongerAgoThan) {
        List<Long> refundIds = refundRepository.findIdsByStatusRequestedBefore(RefundStatus.REQUESTED,
                clock.instant().minus(requestedLongerAgoThan), PageRequest.of(0, RECOVERY_BATCH_SIZE));
        int processed = 0;
        for (Long refundId : refundIds) {
            try {
                RefundResponse refund = processRefund(refundId);
                log.info("Stuck refund processed. refundId={} status={}", refundId, refund.status());
                processed++;
            } catch (OptimisticLockingFailureException e) {
                log.info("Stuck refund taken by another thread. refundId={}", refundId);
            }
        }
        return processed;
    }

    private Long createRefund(Long paymentId, BigDecimal requestedAmount, String reason, RequesterType requesterType,
                              CallerInfo admin, String idempotencyKey) {
        Payment payment = findPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Only successful payments can be refunded (payment " + paymentId
                    + " is " + payment.getStatus() + ")");
        }

        BigDecimal refundable = payment.refundableAmount();
        BigDecimal amount = requestedAmount == null ? refundable : requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(refundable) > 0) {
            throw new BusinessRuleException("Refund amount must be between 0.01 and " + refundable
                    + " (already refunded or in progress: " + payment.getRefundedAmount() + ")");
        }

        BigDecimal refundedBefore = payment.getRefundedAmount();
        payment.setRefundedAmount(refundedBefore.add(amount)); // reserve now, release if the provider refuses

        Refund refund = new Refund();
        refund.setPaymentId(paymentId);
        refund.setOrderId(payment.getOrderId());
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setRequestedByType(requesterType);
        refund.setRequestedByUserId(admin == null ? null : admin.userId());
        refund.setIdempotencyKey(idempotencyKey);
        refundRepository.save(refund);

        if (admin != null) {
            adminAuditService.recordAction(admin, AuditAction.REFUND_INITIATED, AdminAuditService.ENTITY_TYPE_PAYMENT,
                    paymentId, "refundedAmount=" + refundedBefore,
                    "refundedAmount=" + payment.getRefundedAmount() + " (refund " + refund.getId() + ")", reason);
        }
        paymentRepository.flush();
        log.info("Refund requested. refundId={} paymentId={} amount={} by={}", refund.getId(), paymentId, amount, requesterType);
        return refund.getId();
    }

    /** A full refund changes the payment to REFUNDED and publishes it (order-service shows it on the order). */
    private void recordRefundResult(Long refundId, ProviderResult result) {
        Refund refund = findRefund(refundId);
        Payment payment = findPayment(refund.getPaymentId());
        refund.setCompletedAt(clock.instant());

        if (!result.success()) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(result.failureReason());
            payment.setRefundedAmount(payment.getRefundedAmount().subtract(refund.getAmount())); // release the reservation
            log.warn("Refund failed. refundId={} paymentId={} reason={}", refundId, payment.getId(), result.failureReason());
            return;
        }

        refund.setStatus(RefundStatus.SUCCESS);
        refund.setProviderReference(result.providerReference());
        log.info("Refund succeeded. refundId={} paymentId={} amount={}", refundId, payment.getId(), refund.getAmount());

        if (payment.refundableAmount().signum() == 0) {
            PaymentService.changeStatus(payment, PaymentStatus.REFUNDED);
            paymentEventPublisher.statusChanged(payment, refund.getReason());
        }
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private Refund findRefund(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
    }
}
