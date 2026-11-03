package com.fooddelivery.orderservice.service;

import java.util.Locale;

import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.orderservice.dto.response.InternalOrderResponse;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentStatus;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order side of payments: the internal order lookup for payment-service, and the payment results
 * published by payment-service (PaymentStatusChanged), one step of the checkout saga.
 * <p>
 * Kafka may deliver an event twice: {@link ProcessedEventGuard} skips repeats, and each step also
 * checks the order's current state, so applying the same result twice is harmless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    static final String PAYMENT_SUCCESS = "SUCCESS";
    static final String PAYMENT_FAILED = "FAILED";
    static final String PAYMENT_REFUNDED = "REFUNDED";

    private final OrderRepository orderRepository;
    private final OrderStatusTransitions orderStatusTransitions;
    private final OrderEventPublisher orderEventPublisher;
    private final ProcessedEventGuard processedEventGuard;

    @Transactional(readOnly = true)
    public InternalOrderResponse getInternalOrder(Long orderId) {
        Order order = findOrder(orderId);
        return new InternalOrderResponse(order.getId(), order.getCustomerId(), order.getRestaurantId(),
                order.getStatus(), order.getPaymentMethod(), order.getPaymentStatus(), order.getTotalAmount(),
                order.getCurrency(), order.getRestaurantName(), order.getCustomerName(), order.getCustomerPhone(),
                OrderMapper.toAddress(order.getDeliveryAddress()));
    }

    @Transactional
    public void applyPaymentStatusChanged(PaymentStatusChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "PaymentStatusChanged")) {
            return;
        }
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Payment event for an unknown order ignored. orderId={} paymentId={}",
                    event.getOrderId(), event.getPaymentId());
            return;
        }

        String paymentRef = "Payment " + event.getPaymentId();
        switch (event.getStatus()) {
            case PAYMENT_SUCCESS -> applyPaid(order, event.getPaymentId(), paymentRef);
            case PAYMENT_FAILED -> applyFailed(order, paymentRef, event.getReason());
            case PAYMENT_REFUNDED -> applyRefunded(order, paymentRef);
            // Tolerant reader: a status added later in payment-service must not break this consumer.
            default -> log.warn("Unknown payment status ignored. orderId={} paymentId={} status={}",
                    order.getId(), event.getPaymentId(), event.getStatus());
        }
    }

    private void applyPaid(Order order, Long paymentId, String paymentRef) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return; // already applied
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            // Compensation: the order was cancelled (by the customer, an admin or the payment timeout)
            // while the payment was still running. The money must go back to the customer.
            String status = order.getStatus().name().toLowerCase(Locale.ROOT);
            orderStatusTransitions.recordEvent(order, ActorType.SYSTEM, null,
                    paymentRef + " arrived after the order was " + status + ": refund requested");
            orderEventPublisher.orderPaymentRejected(order, paymentId,
                    "Order " + order.getId() + " was " + status + " before the payment completed");
            log.info("Late payment rejected, refund requested. orderId={} {} orderStatus={}",
                    order.getId(), paymentRef, order.getStatus());
            return;
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        orderStatusTransitions.changeStatus(order, OrderStatus.PLACED, ActorType.SYSTEM, null, paymentRef + " received");
        log.info("Order paid. orderId={} {}", order.getId(), paymentRef);
    }

    /** The order stays PAYMENT_PENDING so the customer can try again (or cancel). */
    private void applyFailed(Order order, String paymentRef, String reason) {
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING || order.getPaymentStatus() == PaymentStatus.FAILED) {
            return; // outdated or repeated message: nothing to do
        }
        order.setPaymentStatus(PaymentStatus.FAILED);
        orderStatusTransitions.recordEvent(order, ActorType.SYSTEM, null,
                paymentRef + " failed" + (reason == null ? "" : ": " + reason));
        log.info("Order payment failed. orderId={} {}", order.getId(), paymentRef);
    }

    private void applyRefunded(Order order, String paymentRef) {
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderStatusTransitions.recordEvent(order, ActorType.SYSTEM, null, paymentRef + " refunded");
        log.info("Order refunded. orderId={} {}", order.getId(), paymentRef);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }
}
