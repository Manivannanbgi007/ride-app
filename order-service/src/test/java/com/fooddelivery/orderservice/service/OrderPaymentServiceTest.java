package com.fooddelivery.orderservice.service;

import static com.fooddelivery.orderservice.service.OrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.entity.PaymentStatus;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderTimelineRepository orderTimelineRepository;
    @Mock
    private OrderEventPublisher orderEventPublisher;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private OrderPaymentService orderPaymentService;

    @BeforeEach
    void setUp() {
        orderPaymentService = new OrderPaymentService(orderRepository,
                new OrderStatusTransitions(orderTimelineRepository, orderEventPublisher,
                new OrderMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), Clock.systemUTC()),
                orderEventPublisher, processedEventGuard);
        lenient().when(processedEventGuard.firstDelivery(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void successfulPaymentSendsTheOrderToTheRestaurant() {
        Order order = givenOnlineOrder(OrderStatus.PAYMENT_PENDING, PaymentStatus.PENDING);

        orderPaymentService.applyPaymentStatusChanged(paymentEvent("SUCCESS"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aRepeatedEventIsSkipped() {
        PaymentStatusChanged event = paymentEvent("SUCCESS");
        when(processedEventGuard.firstDelivery(event.getEventId(), "PaymentStatusChanged")).thenReturn(false);

        orderPaymentService.applyPaymentStatusChanged(event);

        verifyNoInteractions(orderRepository, orderTimelineRepository);
    }

    @Test
    void lateSuccessForACancelledOrderAsksPaymentServiceToRefund() {
        Order order = givenOnlineOrder(OrderStatus.CANCELLED, PaymentStatus.PENDING);

        orderPaymentService.applyPaymentStatusChanged(paymentEvent("SUCCESS"));

        // Compensation instead of PLACED: the order stays cancelled and the payment is rejected
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(orderEventPublisher).orderPaymentRejected(eq(order), eq(900L), contains("cancelled"));
    }

    @Test
    void failedPaymentKeepsTheOrderOpenForAnotherTry() {
        Order order = givenOnlineOrder(OrderStatus.PAYMENT_PENDING, PaymentStatus.PENDING);

        orderPaymentService.applyPaymentStatusChanged(paymentEvent("FAILED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void lateFailureAfterSuccessIsIgnored() {
        Order order = givenOnlineOrder(OrderStatus.PLACED, PaymentStatus.PAID);

        orderPaymentService.applyPaymentStatusChanged(paymentEvent("FAILED"));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderTimelineRepository, never()).save(any());
    }

    @Test
    void refundIsShownOnTheOrder() {
        Order order = givenOnlineOrder(OrderStatus.CANCELLED, PaymentStatus.PAID);

        orderPaymentService.applyPaymentStatusChanged(paymentEvent("REFUNDED"));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void anUnknownStatusFromANewerPaymentServiceIsIgnored() {
        Order order = givenOnlineOrder(OrderStatus.PAYMENT_PENDING, PaymentStatus.PENDING);

        orderPaymentService.applyPaymentStatusChanged(paymentEvent("PARTIALLY_CAPTURED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(orderTimelineRepository, never()).save(any());
    }

    private Order givenOnlineOrder(OrderStatus status, PaymentStatus paymentStatus) {
        Order order = order(500L, 7L, 10L, status);
        order.setPaymentMethod(PaymentMethod.UPI);
        order.setPaymentStatus(paymentStatus);
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        return order;
    }

    private static PaymentStatusChanged paymentEvent(String status) {
        return PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setPaymentId(900L)
                .setOrderId(500L)
                .setCustomerId(7L)
                .setStatus(status)
                .setMethod("UPI")
                .setAmount(new BigDecimal("353.95"))
                .setRefundedAmount(new BigDecimal("0.00"))
                .setCurrency("INR")
                .setReason("FAILED".equals(status) ? "Card declined" : null)
                .build();
    }
}
