package com.fooddelivery.orderservice.service;

import static com.fooddelivery.orderservice.service.OrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.OrderTimelineEntry;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.entity.PaymentStatus;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryServiceTest {

    private static final long PARTNER = 77L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderTimelineRepository orderTimelineRepository;
    @Mock
    private OrderEventPublisher orderEventPublisher;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private OrderDeliveryService orderDeliveryService;

    @BeforeEach
    void setUp() {
        orderDeliveryService = new OrderDeliveryService(orderRepository,
                new OrderStatusTransitions(orderTimelineRepository, orderEventPublisher,
                new OrderMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), Clock.systemUTC()),
                processedEventGuard);
        lenient().when(processedEventGuard.firstDelivery(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void pickupSendsTheOrderOutForDelivery() {
        Order order = givenOrder(OrderStatus.READY_FOR_PICKUP, PaymentMethod.UPI, PaymentStatus.PAID);

        orderDeliveryService.applyDeliveryStatusChanged(deliveryEvent("PICKED_UP"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        verify(orderEventPublisher).orderStatusChanged(order, OrderStatus.READY_FOR_PICKUP, ActorType.DELIVERY_PARTNER,
                "Picked up by Ravi Kumar");
    }

    @Test
    void deliveryOfACashOrderAlsoMarksItPaid() {
        Order order = givenOrder(OrderStatus.OUT_FOR_DELIVERY, PaymentMethod.CASH_ON_DELIVERY, PaymentStatus.PENDING);

        orderDeliveryService.applyDeliveryStatusChanged(deliveryEvent("DELIVERED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        ArgumentCaptor<OrderTimelineEntry> timeline = ArgumentCaptor.forClass(OrderTimelineEntry.class);
        verify(orderTimelineRepository, times(2)).save(timeline.capture());
        assertThat(timeline.getAllValues()).extracting(OrderTimelineEntry::getNote)
                .containsExactly("Delivered by Ravi Kumar", "Cash collected: INR 353.95");
    }

    @Test
    void deliveryOfAPaidOnlineOrderLeavesThePaymentAlone() {
        Order order = givenOrder(OrderStatus.OUT_FOR_DELIVERY, PaymentMethod.CARD, PaymentStatus.PAID);

        orderDeliveryService.applyDeliveryStatusChanged(deliveryEvent("DELIVERED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void deliveryWithoutAReportedPickupPassesThroughOutForDelivery() {
        Order order = givenOrder(OrderStatus.READY_FOR_PICKUP, PaymentMethod.UPI, PaymentStatus.PAID);

        orderDeliveryService.applyDeliveryStatusChanged(deliveryEvent("DELIVERED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(orderEventPublisher).orderStatusChanged(order, OrderStatus.READY_FOR_PICKUP, ActorType.DELIVERY_PARTNER,
                "Picked up by Ravi Kumar");
    }

    @Test
    void assignmentIsOnlyATimelineEntry() {
        Order order = givenOrder(OrderStatus.PREPARING, PaymentMethod.UPI, PaymentStatus.PAID);

        orderDeliveryService.applyDeliveryStatusChanged(deliveryEvent("ASSIGNED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        ArgumentCaptor<OrderTimelineEntry> timeline = ArgumentCaptor.forClass(OrderTimelineEntry.class);
        verify(orderTimelineRepository).save(timeline.capture());
        assertThat(timeline.getValue().getNote()).isEqualTo("Delivery assigned to Ravi Kumar");
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void aPickupForACancelledOrderIsIgnored() {
        Order order = givenOrder(OrderStatus.CANCELLED, PaymentMethod.UPI, PaymentStatus.PAID);

        orderDeliveryService.applyDeliveryStatusChanged(deliveryEvent("PICKED_UP"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderTimelineRepository, never()).save(any());
    }

    @Test
    void aRepeatedEventIsSkipped() {
        DeliveryStatusChanged event = deliveryEvent("DELIVERED");
        when(processedEventGuard.firstDelivery(event.getEventId(), "DeliveryStatusChanged")).thenReturn(false);

        orderDeliveryService.applyDeliveryStatusChanged(event);

        verifyNoInteractions(orderRepository, orderTimelineRepository, orderEventPublisher);
    }

    private Order givenOrder(OrderStatus status, PaymentMethod method, PaymentStatus paymentStatus) {
        Order order = order(500L, 7L, 10L, status);
        order.setPaymentMethod(method);
        order.setPaymentStatus(paymentStatus);
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        return order;
    }

    private static DeliveryStatusChanged deliveryEvent(String status) {
        return DeliveryStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setDeliveryId(40L)
                .setOrderId(500L)
                .setCustomerId(7L)
                .setRestaurantId(10L)
                .setStatus(status)
                .setPartnerUserId(PARTNER)
                .setPartnerName("Ravi Kumar")
                .setVehicleType("BIKE")
                .build();
    }
}
