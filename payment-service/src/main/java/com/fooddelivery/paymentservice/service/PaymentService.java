package com.fooddelivery.paymentservice.service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.paymentservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.paymentservice.config.AppProperties;
import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.dto.response.PaymentResponse;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import com.fooddelivery.paymentservice.exception.ConflictException;
import com.fooddelivery.paymentservice.exception.ResourceNotFoundException;
import com.fooddelivery.paymentservice.kafka.producer.PaymentEventPublisher;
import com.fooddelivery.paymentservice.mapper.PaymentMapper;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.fooddelivery.paymentservice.strategy.PaymentProviderGuard;
import com.fooddelivery.paymentservice.strategy.PaymentStrategy;
import com.fooddelivery.paymentservice.strategy.PaymentStrategyFactory;
import com.fooddelivery.paymentservice.strategy.ProviderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A customer paying an order online, exactly once.
 * <ol>
 *   <li>Idempotency: the same Idempotency-Key returns the first payment.</li>
 *   <li>Checks the order (no transaction open): it belongs to the caller and waits for payment. The order comes
 *       from the local copy fed by order events, or from order-service; the amount never comes from the client.</li>
 *   <li>Record the attempt (INITIATED). Unique keys block duplicates and a second payment for the same order.</li>
 *   <li>PROCESSING, then the provider is called, with no database transaction open.</li>
 *   <li>Result + PaymentStatusChanged event saved in ONE transaction (transactional outbox). The outbox relay
 *       publishes the event and order-service moves the order on: the next step of the checkout saga.</li>
 * </ol>
 * This is the simulated gateway (app.payment.provider=SIMULATED, the default). With RAZORPAY, online payments go
 * through {@link RazorpayPaymentService} and this endpoint refuses them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Statuses that block another payment for the same order (the same rule as the active_order_id unique key). */
    private static final Set<PaymentStatus> ACTIVE_STATUSES =
            EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.PROCESSING, PaymentStatus.SUCCESS);

    private final PaymentRepository paymentRepository;
    private final PayableOrderDirectory payableOrderDirectory;
    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentProviderGuard paymentProviderGuard;
    private final PaymentMetrics paymentMetrics;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMapper paymentMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties appProperties;

    /** @param replayed true when the payment already existed for this Idempotency-Key */
    public record PaymentResult(PaymentResponse payment, boolean replayed) {
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // any provider failure is a declined payment
    public PaymentResult pay(Long customerId, String idempotencyKey, PayRequest request) {
        String fingerprint = PaymentFingerprint.of(request);
        Optional<PaymentResult> replay = findExisting(customerId, idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (appProperties.payment().razorpayEnabled()) {
            // With a real gateway, the simulated one must not be able to mark an order paid
            throw new BusinessRuleException("Online payments go through Razorpay here: "
                    + "use POST /api/v1/payments/razorpay/orders, then Razorpay Checkout");
        }

        OrderSummary order = payableOrderDirectory.findOrder(request.orderId())
                .filter(found -> customerId.equals(found.customerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId()));
        if (!"PAYMENT_PENDING".equals(order.status())) {
            throw new ConflictException("Order " + order.id() + " is not waiting for payment (status " + order.status() + ")");
        }
        // The local copy does not follow the order once it is paid: our own payments tell.
        if (paymentRepository.existsByOrderIdAndStatusIn(order.id(), ACTIVE_STATUSES)) {
            throw new ConflictException("A payment for order " + order.id() + " is already in progress or completed");
        }
        if (!request.paymentMethod().name().equals(order.paymentMethod())) {
            throw new BusinessRuleException("This order is paid by " + order.paymentMethod() + " (chosen at checkout)");
        }
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(request.paymentMethod());
        String instrument = strategy.validateAndDescribe(request);

        Long paymentId;
        try {
            paymentId = transactionTemplate.execute(transaction -> paymentRepository.saveAndFlush(
                    newPayment(customerId, order, request, instrument, idempotencyKey, fingerprint)).getId());
        } catch (DataIntegrityViolationException e) {
            // Either the same key at the same moment (return that payment),
            // or another payment for this order is running or done (refuse: no double charging).
            return findExisting(customerId, idempotencyKey, fingerprint).orElseThrow(() ->
                    new ConflictException("A payment for order " + order.id() + " is already in progress or completed"));
        }

        transactionTemplate.executeWithoutResult(transaction -> {
            Payment payment = findPayment(paymentId);
            changeStatus(payment, PaymentStatus.PROCESSING);
            payment.setProcessingStartedAt(clock.instant());
        });

        ProviderResult result;
        try {
            result = paymentProviderGuard.charge(() -> strategy.charge(request, order.totalAmount()));
        } catch (RuntimeException e) {
            // With a real provider, an error here may mean "unknown outcome": the payment would be
            // reconciled with the provider before being marked FAILED. The simulator never throws.
            log.error("Payment provider error. paymentId={}", paymentId, e);
            result = ProviderResult.declined("Payment provider error, please try again");
        }

        ProviderResult providerResult = result;
        transactionTemplate.executeWithoutResult(transaction -> recordResult(paymentId, providerResult));
        paymentMetrics.paymentCompleted(request.paymentMethod().name(), providerResult.success());

        PaymentResponse response = paymentMapper.toResponse(findPayment(paymentId));
        log.info("Payment finished. paymentId={} orderId={} status={} amount={}",
                paymentId, order.id(), response.status(), response.amount());
        return new PaymentResult(response, false);
    }

    public List<PaymentResponse> getMyPayments(Long customerId, Long orderId) {
        List<Payment> payments = orderId == null
                ? paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                : paymentRepository.findByCustomerIdAndOrderIdOrderByCreatedAtDesc(customerId, orderId);
        return payments.stream().map(paymentMapper::toResponse).toList();
    }

    public PaymentResponse getMyPayment(Long customerId, Long paymentId) {
        return paymentRepository.findByIdAndCustomerId(paymentId, customerId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private Optional<PaymentResult> findExisting(Long customerId, String idempotencyKey, String fingerprint) {
        return paymentRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(existing -> {
                    if (!existing.getRequestFingerprint().equals(fingerprint)) {
                        throw new BusinessRuleException("This Idempotency-Key was already used for a different payment");
                    }
                    return new PaymentResult(paymentMapper.toResponse(existing), true);
                });
    }

    /** The result and its event are committed together: order-service learns about every result. */
    private void recordResult(Long paymentId, ProviderResult result) {
        Payment payment = findPayment(paymentId);
        payment.setCompletedAt(clock.instant());
        if (result.success()) {
            changeStatus(payment, PaymentStatus.SUCCESS);
            payment.setProviderReference(result.providerReference());
        } else {
            changeStatus(payment, PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
        }
        paymentEventPublisher.statusChanged(payment, result.failureReason());
    }

    private Payment newPayment(Long customerId, OrderSummary order, PayRequest request, String instrument,
                               String idempotencyKey, String fingerprint) {
        Payment payment = new Payment();
        payment.setOrderId(order.id());
        payment.setCustomerId(customerId);
        payment.setAmount(order.totalAmount());
        payment.setCurrency(order.currency());
        payment.setMethod(request.paymentMethod());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInstrument(instrument);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setRequestFingerprint(fingerprint);
        return payment;
    }

    static void changeStatus(Payment payment, PaymentStatus newStatus) {
        if (!payment.getStatus().canChangeTo(newStatus)) {
            throw new ConflictException(
                    "Payment " + payment.getId() + " cannot change from " + payment.getStatus() + " to " + newStatus);
        }
        payment.setStatus(newStatus);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }
}
