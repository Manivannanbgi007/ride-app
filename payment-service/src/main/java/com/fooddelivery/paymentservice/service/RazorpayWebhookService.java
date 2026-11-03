package com.fooddelivery.paymentservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.paymentservice.client.RazorpayClient;
import com.fooddelivery.paymentservice.client.RazorpayClient.RazorpayPayment;
import com.fooddelivery.paymentservice.client.RazorpaySignatures;
import com.fooddelivery.paymentservice.client.RazorpayWebhookEvent;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.exception.AuthenticationFailedException;
import com.fooddelivery.paymentservice.exception.InvalidRequestException;
import com.fooddelivery.paymentservice.exception.PaymentGatewayException;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.fooddelivery.paymentservice.repository.ProcessedEventRepository;
import com.fooddelivery.paymentservice.service.RazorpayPaymentService.Applied;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay webhooks: Razorpay calls POST /api/v1/payments/razorpay/webhook when something happens to a payment,
 * also when the customer closed the browser before our page could confirm it.
 * <ol>
 *   <li>The X-Razorpay-Signature header must be the HMAC-SHA256 of the exact body with the webhook secret (set
 *       in the Dashboard and in RAZORPAY_WEBHOOK_SECRET). Anything else is refused (401) and changes nothing.</li>
 *   <li>Razorpay may send the same event more than once (and retries when we do not answer 2xx). Each event id
 *       (X-Razorpay-Event-Id) is remembered in processed_events, like Kafka events; a repeat is answered
 *       DUPLICATE. The payment change itself is idempotent too: a SUCCESS payment stays SUCCESS.</li>
 *   <li>payment.authorized, payment.captured, order.paid and payment.failed are applied exactly like the
 *       browser's verification ({@link RazorpayPaymentService#applyPayment}): same checks of order, amount and
 *       currency, capture of held money, SUCCESS only once Razorpay reports the payment captured.</li>
 * </ol>
 * An event is remembered only after it was applied: when the capture did not go through, we answer 502 and
 * Razorpay sends the event again later (it retries for 24 hours).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayWebhookService {

    /** The events this service uses; subscribe to them in the Dashboard (others are answered IGNORED). */
    static final Set<String> PAYMENT_EVENTS =
            Set.of("payment.authorized", "payment.captured", "payment.failed", "order.paid");
    /** processed_events.event_id is 36 characters: "rzp-" + Razorpay's id ("evt_" + 14) fits. */
    private static final String EVENT_ID_PREFIX = "rzp-";
    private static final int MAX_EVENT_ID_LENGTH = 36;

    private final RazorpayClient razorpayClient;
    private final RazorpayPaymentService razorpayPaymentService;
    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ProcessedEventGuard processedEventGuard;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    /** What happened to one webhook call. */
    public enum Result { PROCESSED, DUPLICATE, IGNORED }

    /**
     * @param payload   the request body exactly as received (the signature is over these bytes)
     * @param signature the X-Razorpay-Signature header
     * @param eventId   the X-Razorpay-Event-Id header (may be missing: then the body identifies the event)
     */
    public Result receive(String payload, String signature, String eventId) {
        String webhookSecret = razorpayClient.webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Razorpay webhook refused: RAZORPAY_WEBHOOK_SECRET is not set, so it cannot be verified");
            throw new AuthenticationFailedException("Webhook signature cannot be verified");
        }
        if (!RazorpaySignatures.isValidWebhookSignature(payload, signature, webhookSecret)) {
            log.warn("Razorpay webhook refused: the signature does not match. eventId={}", eventId);
            throw new AuthenticationFailedException("Invalid webhook signature");
        }

        String processedId = processedIdOf(eventId, payload);
        if (processedEventRepository.existsById(processedId)) {
            log.info("Razorpay webhook repeated, ignored. eventId={}", processedId);
            return Result.DUPLICATE;
        }
        RazorpayWebhookEvent event = parse(payload);
        Result result = apply(event);
        remember(processedId, event.event());
        return result;
    }

    private Result apply(RazorpayWebhookEvent event) {
        RazorpayPayment razorpayPayment = event.payment();
        if (!PAYMENT_EVENTS.contains(event.event()) || razorpayPayment == null || razorpayPayment.orderId() == null) {
            log.info("Razorpay webhook not used here. event={}", event.event());
            return Result.IGNORED;
        }
        Optional<Payment> payment = paymentRepository.findByProviderOrderId(razorpayPayment.orderId());
        if (payment.isEmpty()) {
            // For example a payment made on the same Razorpay account by another application
            log.warn("Razorpay webhook for an unknown Razorpay order ignored. event={} razorpayOrderId={}",
                    event.event(), razorpayPayment.orderId());
            return Result.IGNORED;
        }
        Applied applied = razorpayPaymentService.applyPayment(payment.get().getId(), razorpayPayment);
        log.info("Razorpay webhook applied. event={} paymentId={} razorpayPaymentId={} result={}",
                event.event(), payment.get().getId(), razorpayPayment.id(), applied);
        if (applied == Applied.PENDING && razorpayPayment.isAuthorized()) {
            // The money is held but the capture failed: not remembered, so Razorpay's retry tries again
            throw new PaymentGatewayException("Razorpay payment " + razorpayPayment.id() + " is not captured yet");
        }
        return applied == Applied.NOT_OURS ? Result.IGNORED : Result.PROCESSED;
    }

    private RazorpayWebhookEvent parse(String payload) {
        try {
            RazorpayWebhookEvent event = jsonMapper.readValue(payload, RazorpayWebhookEvent.class);
            if (event == null || event.event() == null) {
                throw new InvalidRequestException("The webhook has no event name");
            }
            return event;
        } catch (JacksonException e) {
            throw new InvalidRequestException("The webhook body is not valid JSON");
        }
    }

    /** Saved in its own transaction; two copies of one event arriving together end up with one row. */
    private void remember(String processedId, String eventName) {
        try {
            transactionTemplate.executeWithoutResult(transaction ->
                    processedEventGuard.firstDelivery(processedId, "Razorpay " + eventName));
        } catch (DataIntegrityViolationException e) {
            log.info("Razorpay webhook remembered by a parallel delivery. eventId={}", processedId);
        }
    }

    /** "rzp-evt_..." from the header; without it, a hash of the body (a resent event has the same body). */
    private static String processedIdOf(String eventId, String payload) {
        String id = eventId == null || eventId.isBlank()
                ? sha256(payload)
                : eventId.trim();
        String processedId = EVENT_ID_PREFIX + id;
        return processedId.length() <= MAX_EVENT_ID_LENGTH ? processedId : EVENT_ID_PREFIX + sha256(id);
    }

    /** The first 32 hex characters of the SHA-256: "rzp-" + 32 = 36, the column size. */
    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, MAX_EVENT_ID_LENGTH - EVENT_ID_PREFIX.length());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
