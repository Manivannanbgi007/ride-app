package com.fooddelivery.paymentservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fooddelivery.paymentservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.paymentservice.client.RazorpayClient;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayOrder;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayPayment;
import com.fooddelivery.paymentservice.client.RazorpaySignatures;
import com.fooddelivery.paymentservice.dto.response.PaymentResponse;
import com.fooddelivery.paymentservice.dto.response.RazorpayCheckoutResponse;
import com.fooddelivery.paymentservice.dto.response.RazorpayConfigResponse;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import com.fooddelivery.paymentservice.exception.ConflictException;
import com.fooddelivery.paymentservice.exception.PaymentGatewayException;
import com.fooddelivery.paymentservice.exception.ResourceNotFoundException;
import com.fooddelivery.paymentservice.kafka.producer.PaymentEventPublisher;
import com.fooddelivery.paymentservice.mapper.PaymentMapper;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.fooddelivery.paymentservice.strategy.PaymentProviderGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Online payments with Razorpay Checkout (app.payment.provider=RAZORPAY), in three steps:
 * <ol>
 *   <li>{@link #startCheckout}: checks the order like {@link PaymentService} does (the caller's own, waiting for
 *       payment; the amount comes from the order, never from the browser), saves a payment (INITIATED), creates a
 *       Razorpay order for exactly that amount and marks the payment PROCESSING: the customer pays in Checkout now.
 *       Asking again returns the same Razorpay order, so one order is never charged twice.</li>
 *   <li>The browser opens Checkout. When the customer has paid, Checkout hands it razorpay_payment_id,
 *       razorpay_order_id and razorpay_signature.</li>
 *   <li>{@link #confirmPayment}: the signature must be right (it proves the ids come from Razorpay), then Razorpay
 *       itself is asked about the payment: it must belong to this Razorpay order, for this amount and currency. An
 *       <i>authorized</i> payment (the bank approved; the money is only held) is <i>captured</i> here (the money is
 *       taken). Only a <i>captured</i> payment makes ours SUCCESS, saved together with the PaymentStatusChanged event
 *       that lets order-service confirm the order (transactional outbox).</li>
 * </ol>
 * {@link RazorpayWebhookService} reaches the same result from Razorpay's side, for a customer who closed the page too
 * early. Whichever comes first wins; the other finds the payment SUCCESS already (idempotent).
 * <p>
 * A failed or cancelled attempt does not end the payment: it stays PROCESSING (with the reason) and the customer can
 * try again in Checkout, on the same Razorpay order. order-service cancels an order not paid within 15 minutes; a
 * payment that still arrives after that is refunded automatically (OrderPaymentRejected -> {@link RefundService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayPaymentService {

    /** Statuses that keep an order busy: one running or successful payment per order (active_order_id key). */
    private static final Set<PaymentStatus> ACTIVE_STATUSES =
            EnumSet.of(PaymentStatus.INITIATED, PaymentStatus.PROCESSING, PaymentStatus.SUCCESS);
    /** This integration takes Indian rupees (international payments need a separate Razorpay approval). */
    private static final String CURRENCY = "INR";
    private static final int MAX_REASON_LENGTH = 255;
    private static final int MAX_INSTRUMENT_LENGTH = 60;

    private final PaymentRepository paymentRepository;
    private final PayableOrderDirectory payableOrderDirectory;
    private final RazorpayClient razorpayClient;
    private final PaymentProviderGuard paymentProviderGuard;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMetrics paymentMetrics;
    private final PaymentMapper paymentMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /** Whether the browser should open Razorpay Checkout, and with which public key id. */
    public RazorpayConfigResponse config() {
        if (!razorpayClient.isEnabled()) {
            return new RazorpayConfigResponse(false, null, false);
        }
        String keyId = razorpayClient.keyId();
        return new RazorpayConfigResponse(true, keyId, keyId.startsWith("rzp_test_"));
    }

    /** Step 1: a Razorpay order for the customer's order (or the one already started for it). */
    @SuppressWarnings("checkstyle:IllegalCatch") // whatever went wrong, nothing was charged: the payment fails
    public RazorpayCheckoutResponse startCheckout(Long customerId, Long orderId) {
        requireRazorpay();
        OrderSummary order = payableOrderDirectory.findOrder(orderId)
                .filter(found -> customerId.equals(found.customerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!"PAYMENT_PENDING".equals(order.status())) {
            throw new ConflictException("Order " + orderId + " is not waiting for payment (status " + order.status() + ")");
        }
        PaymentMethod method = onlineMethodOf(order);
        if (!CURRENCY.equals(order.currency())) {
            throw new BusinessRuleException("Razorpay takes payments in " + CURRENCY + " here, not " + order.currency());
        }

        Optional<Payment> active = paymentRepository.findFirstByOrderIdAndStatusInOrderByIdDesc(orderId, ACTIVE_STATUSES);
        if (active.isPresent()) {
            return reuseOrRefuse(active.get(), order);
        }

        Long paymentId;
        try {
            paymentId = transactionTemplate.execute(transaction ->
                    paymentRepository.saveAndFlush(newPayment(customerId, order, method)).getId());
        } catch (DataIntegrityViolationException e) {
            // A double click or a second tab started it a moment ago (the active_order_id unique key)
            return paymentRepository.findFirstByOrderIdAndStatusInOrderByIdDesc(orderId, ACTIVE_STATUSES)
                    .map(existing -> reuseOrRefuse(existing, order))
                    .orElseThrow(() -> new ConflictException("A payment for order " + orderId + " is already in progress"));
        }

        long amount = RazorpayClient.toPaise(order.totalAmount());
        RazorpayOrder razorpayOrder;
        try {
            razorpayOrder = paymentProviderGuard.call(() -> razorpayClient.createOrder(amount, order.currency(),
                    "order-" + orderId + "-payment-" + paymentId,
                    Map.of("orderId", String.valueOf(orderId), "paymentId", String.valueOf(paymentId))));
            if (razorpayOrder.amount() != amount || !order.currency().equals(razorpayOrder.currency())) {
                throw new PaymentGatewayException("Razorpay order " + razorpayOrder.id() + " has another amount");
            }
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(transaction ->
                    failPayment(paymentId, "Could not start the payment with Razorpay. Please try again."));
            throw e;
        }

        Payment started = transactionTemplate.execute(transaction -> {
            Payment payment = findPayment(paymentId);
            payment.setProviderOrderId(razorpayOrder.id());
            PaymentService.changeStatus(payment, PaymentStatus.PROCESSING);
            payment.setProcessingStartedAt(clock.instant());
            return payment;
        });
        log.info("Razorpay checkout started. paymentId={} orderId={} razorpayOrderId={} amountPaise={}",
                paymentId, orderId, razorpayOrder.id(), amount);
        return checkoutOf(started);
    }

    /**
     * Step 3: what Checkout reported, verified. The signature must be right; then Razorpay's own answer about the
     * payment decides. When Razorpay cannot be asked right now, the payment stays PROCESSING (pending) and the webhook
     * completes it.
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // Razorpay unreachable or refusing: the payment stays pending
    public PaymentResponse confirmPayment(Long customerId, String razorpayOrderId, String razorpayPaymentId,
                                          String signature) {
        requireRazorpay();
        Payment payment = paymentRepository.findByProviderOrderIdAndCustomerId(razorpayOrderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment for Razorpay order " + razorpayOrderId));
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            return paymentMapper.toResponse(payment); // decided already (the webhook or another tab was first)
        }
        if (!RazorpaySignatures.isValidPaymentSignature(razorpayOrderId, razorpayPaymentId, signature,
                razorpayClient.keySecret())) {
            log.warn("Razorpay payment signature does not match. paymentId={} razorpayPaymentId={}",
                    payment.getId(), razorpayPaymentId);
            throw new BusinessRuleException("The payment could not be verified. If money was taken, it is "
                    + "confirmed or refunded automatically.");
        }

        RazorpayPayment razorpayPayment;
        try {
            razorpayPayment = paymentProviderGuard.call(() -> razorpayClient.fetchPayment(razorpayPaymentId));
        } catch (RuntimeException e) {
            log.warn("Razorpay payment not checked yet, left pending. paymentId={} reason={}",
                    payment.getId(), e.getMessage());
            return paymentMapper.toResponse(findPayment(payment.getId()));
        }
        applyPayment(payment.getId(), razorpayPayment);
        return paymentMapper.toResponse(findPayment(payment.getId()));
    }

    /**
     * What Razorpay says about one payment of a payment's Razorpay order, applied: captured -> SUCCESS; authorized
     * (the bank approved, the money is only held) -> captured first, then SUCCESS; failed -> the reason is kept and
     * the payment stays open for another try. A payment of another order, amount or currency changes nothing.
     * Used by the verification here and by {@link RazorpayWebhookService}.
     *
     * @return what changed; {@link Applied#PENDING} when the money is only held still (the capture did not go
     *         through) or Razorpay has no result yet
     */
    Applied applyPayment(Long paymentId, RazorpayPayment razorpayPayment) {
        Payment payment = findPayment(paymentId);
        if (!belongsTo(payment, razorpayPayment)) {
            log.error("Razorpay payment does not match ours: ignored. paymentId={} razorpayOrderId={} amountPaise={} "
                    + "currency={} got razorpayOrderId={} amountPaise={} currency={}", paymentId,
                    payment.getProviderOrderId(), RazorpayClient.toPaise(payment.getAmount()), payment.getCurrency(),
                    razorpayPayment.orderId(), razorpayPayment.amount(), razorpayPayment.currency());
            return Applied.NOT_OURS;
        }
        RazorpayPayment current = razorpayPayment;
        if (current.isAuthorized()) {
            current = capture(paymentId, current);
        }
        if (current.isCaptured()) {
            markSucceeded(paymentId, current);
            return Applied.SUCCEEDED;
        }
        if (current.isFailed()) {
            recordFailedAttempt(paymentId, current);
            return Applied.ATTEMPT_FAILED;
        }
        log.info("Razorpay payment not captured yet. paymentId={} razorpayStatus={}", paymentId, current.status());
        return Applied.PENDING;
    }

    /** The result of {@link #applyPayment}. */
    enum Applied { SUCCEEDED, ATTEMPT_FAILED, PENDING, NOT_OURS }

    /**
     * Takes the held money. When the capture call fails, Razorpay is asked once more: with automatic capture switched
     * on in the Dashboard, Razorpay may have captured the payment itself in the meantime.
     */
    @SuppressWarnings("checkstyle:IllegalCatch") // a capture that did not work leaves the payment authorized
    private RazorpayPayment capture(Long paymentId, RazorpayPayment authorized) {
        try {
            return paymentProviderGuard.call(() -> razorpayClient.capturePayment(authorized.id(), authorized.amount(),
                    authorized.currency()));
        } catch (RuntimeException e) {
            log.warn("Razorpay capture failed. paymentId={} reason={}", paymentId, e.getMessage());
        }
        try {
            return paymentProviderGuard.call(() -> razorpayClient.fetchPayment(authorized.id()));
        } catch (RuntimeException e) {
            log.warn("Razorpay payment left authorized (pending). paymentId={} reason={}", paymentId, e.getMessage());
            return authorized;
        }
    }

    /** The money is taken: SUCCESS, and order-service is told through the outbox. Safe to call twice. */
    private void markSucceeded(Long paymentId, RazorpayPayment captured) {
        try {
            PaymentMethod method = transactionTemplate.execute(transaction -> {
                Payment payment = findPayment(paymentId);
                if (payment.getStatus() != PaymentStatus.PROCESSING) {
                    return null; // SUCCESS already: the other path (verification or webhook) was first
                }
                PaymentService.changeStatus(payment, PaymentStatus.SUCCESS);
                payment.setProviderReference(captured.id());
                payment.setInstrument(describe(captured));
                payment.setFailureReason(null);
                payment.setCompletedAt(clock.instant());
                paymentEventPublisher.statusChanged(payment, null);
                return payment.getMethod();
            });
            if (method != null) {
                paymentMetrics.paymentCompleted(method.name(), true);
                log.info("Razorpay payment captured. paymentId={} razorpayPaymentId={}", paymentId, captured.id());
            }
        } catch (OptimisticLockingFailureException e) {
            // The webhook and the verification arrived together: the other one saved the success
            log.info("Razorpay success saved by the other path. paymentId={}", paymentId);
        }
    }

    /** One attempt failed (declined card, wrong UPI PIN, ...): the reason is kept, the customer can try again. */
    private void recordFailedAttempt(Long paymentId, RazorpayPayment failed) {
        try {
            transactionTemplate.executeWithoutResult(transaction -> {
                Payment payment = findPayment(paymentId);
                if (payment.getStatus() == PaymentStatus.PROCESSING) {
                    String reason = failed.errorDescription() == null ? "The payment failed" : failed.errorDescription();
                    payment.setFailureReason(limit("Razorpay: " + reason, MAX_REASON_LENGTH));
                }
            });
        } catch (OptimisticLockingFailureException e) {
            return; // the payment changed meanwhile (another attempt succeeded): the reason no longer matters
        }
        log.info("Razorpay payment attempt failed. paymentId={} reason={}", paymentId, failed.errorDescription());
    }

    private void failPayment(Long paymentId, String reason) {
        Payment payment = findPayment(paymentId);
        PaymentService.changeStatus(payment, PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setCompletedAt(clock.instant());
        paymentEventPublisher.statusChanged(payment, reason);
    }

    /** An open Razorpay payment of this customer is opened again; anything else running blocks a new one. */
    private RazorpayCheckoutResponse reuseOrRefuse(Payment active, OrderSummary order) {
        if (active.getStatus() == PaymentStatus.SUCCESS) {
            throw new ConflictException("Order " + order.id() + " is already paid");
        }
        if (active.getProvider() == PaymentProvider.RAZORPAY && active.getProviderOrderId() != null
                && active.getCustomerId().equals(order.customerId())) {
            log.info("Razorpay checkout opened again. paymentId={} razorpayOrderId={}",
                    active.getId(), active.getProviderOrderId());
            return checkoutOf(active);
        }
        throw new ConflictException("A payment for order " + order.id() + " is being prepared. Try again in a moment.");
    }

    private RazorpayCheckoutResponse checkoutOf(Payment payment) {
        return new RazorpayCheckoutResponse(payment.getId(), payment.getOrderId(), payment.getProviderOrderId(),
                razorpayClient.keyId(), RazorpayClient.toPaise(payment.getAmount()), payment.getCurrency(),
                razorpayClient.merchantName(), "Order #" + payment.getOrderId(),
                payment.getMethod().name().toLowerCase(Locale.ROOT));
    }

    private Payment newPayment(Long customerId, OrderSummary order, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setOrderId(order.id());
        payment.setCustomerId(customerId);
        payment.setAmount(order.totalAmount());
        payment.setCurrency(order.currency());
        payment.setMethod(method);
        payment.setProvider(PaymentProvider.RAZORPAY);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInstrument("Razorpay Checkout");
        // Required by the table; repeat calls are recognised by the order instead (one open payment per order)
        payment.setIdempotencyKey("razorpay-" + UUID.randomUUID());
        payment.setRequestFingerprint(sha256("razorpay:" + order.id()));
        return payment;
    }

    /** CARD, UPI or WALLET as chosen at checkout; cash on delivery is never paid online. */
    private static PaymentMethod onlineMethodOf(OrderSummary order) {
        PaymentMethod method = Arrays.stream(PaymentMethod.values())
                .filter(candidate -> candidate.name().equals(order.paymentMethod()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Order " + order.id() + " has no online payment method"));
        if (!method.isOnline()) {
            throw new BusinessRuleException("Order " + order.id() + " is paid in cash on delivery");
        }
        return method;
    }

    private static boolean belongsTo(Payment payment, RazorpayPayment razorpayPayment) {
        return payment.getProviderOrderId() != null
                && payment.getProviderOrderId().equals(razorpayPayment.orderId())
                && RazorpayClient.toPaise(payment.getAmount()) == razorpayPayment.amount()
                && payment.getCurrency().equalsIgnoreCase(razorpayPayment.currency());
    }

    /** What the customer paid with, masked like the simulated gateway's ("UPI me***@okhdfc"). */
    static String describe(RazorpayPayment payment) {
        String method = payment.method() == null ? "" : payment.method();
        String description = switch (method) {
            case "upi" -> "UPI " + maskVpa(payment.vpa());
            case "card" -> "Card via Razorpay";
            case "wallet" -> "Wallet " + (payment.wallet() == null ? "" : payment.wallet());
            case "netbanking" -> "Net banking " + (payment.bank() == null ? "" : payment.bank());
            default -> "Razorpay " + method;
        };
        return limit(description.trim(), MAX_INSTRUMENT_LENGTH);
    }

    private static String maskVpa(String vpa) {
        if (vpa == null || !vpa.contains("@")) {
            return "";
        }
        int at = vpa.indexOf('@');
        return vpa.substring(0, Math.min(2, at)) + "***" + vpa.substring(at);
    }

    private void requireRazorpay() {
        if (!razorpayClient.isEnabled()) {
            throw new BusinessRuleException("Razorpay is not switched on here: online payments use POST /api/v1/payments");
        }
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private static String limit(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
