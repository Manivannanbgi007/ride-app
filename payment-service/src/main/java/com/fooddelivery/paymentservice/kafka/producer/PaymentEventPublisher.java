package com.fooddelivery.paymentservice.kafka.producer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds the payment events and writes them to the transactional outbox, in the caller's transaction;
 * {@link OutboxRelay} publishes them to Kafka. The key is the order id, so all payments of one order
 * reach order-service in order.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private static final String AGGREGATE_PAYMENT = "PAYMENT";

    /** Scale of the Avro decimal fields (see PaymentStatusChanged.avsc). */
    private static final int MONEY_SCALE = 2;

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    /** The payment reached SUCCESS, FAILED or REFUNDED. */
    public void statusChanged(Payment payment, String reason) {
        PaymentStatusChanged event = PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setPaymentId(payment.getId())
                .setOrderId(payment.getOrderId())
                .setCustomerId(payment.getCustomerId())
                .setStatus(payment.getStatus().name())
                .setMethod(payment.getMethod().name())
                .setAmount(money(payment.getAmount()))
                .setRefundedAmount(money(payment.getRefundedAmount()))
                .setCurrency(payment.getCurrency())
                .setReason(reason)
                .build();

        outboxWriter.append(AGGREGATE_PAYMENT, payment.getId(), Topics.PAYMENT_STATUS_CHANGED,
                String.valueOf(payment.getOrderId()), event);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
