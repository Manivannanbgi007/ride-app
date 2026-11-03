package com.fooddelivery.paymentservice.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderPaymentRejected;
import com.fooddelivery.paymentservice.entity.PayableOrder;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import com.fooddelivery.paymentservice.repository.PayableOrderRepository;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * payment-service's steps in the checkout saga, driven by order-service's events.
 * <p>
 * Each event is applied in one transaction together with its id ({@link ProcessedEventGuard}), so a
 * redelivered event changes nothing. Refunds are requested inside that transaction and sent to the
 * provider after the commit; if the service stops in between, the refund recovery job sends them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventService {

    /** Size of refunds.reason. */
    private static final int MAX_REASON_LENGTH = 500;

    private final PayableOrderRepository payableOrderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final ProcessedEventGuard processedEventGuard;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /** Keeps the order, so the customer can pay it without payment-service asking order-service. */
    public void onOrderCreated(OrderCreated event) {
        transactionTemplate.executeWithoutResult(transaction -> {
            if (!processedEventGuard.firstDelivery(event.getEventId(), "OrderCreated")) {
                return;
            }
            if (payableOrderRepository.existsById(event.getOrderId())) {
                return; // OrderCancelled arrived first (another topic): the order stays closed
            }
            payableOrderRepository.save(PayableOrder.of(event.getOrderId(), event.getCustomerId(),
                    event.getRestaurantId(), event.getPaymentMethod(), event.getTotalAmount(), event.getCurrency(),
                    event.getStatus(), clock.instant()));
            log.info("Order copy saved. orderId={} status={}", event.getOrderId(), event.getStatus());
        });
    }

    /** The order can no longer be paid; if it was paid, the payment is refunded (compensation). */
    public void onOrderCancelled(OrderCancelled event) {
        Optional<Long> refundId = transactionTemplate.execute(transaction -> {
            if (!processedEventGuard.firstDelivery(event.getEventId(), "OrderCancelled")) {
                return Optional.<Long>empty();
            }
            PayableOrder order = payableOrderRepository.findById(event.getOrderId())
                    .orElseGet(() -> PayableOrder.of(event.getOrderId(), event.getCustomerId(), event.getRestaurantId(),
                            event.getPaymentMethod(), event.getTotalAmount(), event.getCurrency(),
                            event.getFinalStatus(), clock.instant()));
            order.close(event.getFinalStatus(), clock.instant());
            payableOrderRepository.save(order);

            // order-service owns the order's payment state. A payment still running when the order was
            // cancelled is not PAID there: order-service rejects it when it completes (OrderPaymentRejected).
            if (!"PAID".equals(event.getPaymentStatus())) {
                log.info("Cancelled order needs no refund. orderId={} paymentStatus={}",
                        event.getOrderId(), event.getPaymentStatus());
                return Optional.<Long>empty();
            }
            return paymentRepository.findByOrderIdAndStatus(event.getOrderId(), PaymentStatus.SUCCESS)
                    .flatMap(payment -> refundService.requestAutomaticRefund(payment.getId(), cancellationReason(event)));
        });
        refundId.ifPresent(refundService::processRefund);
    }

    /** A payment succeeded for an order that no longer accepted it: the money goes back (compensation). */
    public void onOrderPaymentRejected(OrderPaymentRejected event) {
        Optional<Long> refundId = transactionTemplate.execute(transaction -> {
            if (!processedEventGuard.firstDelivery(event.getEventId(), "OrderPaymentRejected")) {
                return Optional.<Long>empty();
            }
            return refundService.requestAutomaticRefund(event.getPaymentId(), limit(event.getReason()));
        });
        refundId.ifPresent(refundService::processRefund);
    }

    /** For example "Order 42 rejected by restaurant: Kitchen closed early". */
    private static String cancellationReason(OrderCancelled event) {
        return limit("Order " + event.getOrderId() + " " + event.getFinalStatus().toLowerCase(Locale.ROOT)
                + " by " + event.getCancelledBy().toLowerCase(Locale.ROOT)
                + (event.getReason() == null ? "" : ": " + event.getReason()));
    }

    private static String limit(String reason) {
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
