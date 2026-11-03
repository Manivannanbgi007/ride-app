package com.fooddelivery.orderservice.service;

import static com.fooddelivery.orderservice.service.OrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;

import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.AuditAction;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.Role;
import com.fooddelivery.orderservice.exception.ConflictException;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import com.fooddelivery.orderservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerAndAdminOrderServiceTest {

    private static final Long CUSTOMER = 7L;
    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "10.0.0.1", "req-1");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderTimelineRepository orderTimelineRepository;
    @Mock
    private OrderEventPublisher orderEventPublisher;
    @Mock
    private AdminAuditService adminAuditService;

    private CustomerOrderService customerOrderService;
    private AdminOrderService adminOrderService;

    @BeforeEach
    void setUp() {
        OrderStatusTransitions transitions = new OrderStatusTransitions(orderTimelineRepository, orderEventPublisher,
                new OrderMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                Clock.systemUTC());
        customerOrderService = new CustomerOrderService(orderRepository, orderTimelineRepository, transitions, new OrderMapper());
        adminOrderService = new AdminOrderService(orderRepository, orderTimelineRepository, transitions, adminAuditService,
                new OrderMapper(), TestProperties.appProperties());
    }

    @Test
    void customerCancelsBeforeTheRestaurantAccepts() {
        Order order = order(500L, CUSTOMER, 10L, OrderStatus.PLACED);
        when(orderRepository.findByIdAndCustomerId(500L, CUSTOMER)).thenReturn(Optional.of(order));

        OrderResponse response = customerOrderService.cancelMyOrder(CUSTOMER, 500L, "Ordered by mistake");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelledBy()).isEqualTo(ActorType.CUSTOMER);
        verify(orderEventPublisher).orderCancelled(order, OrderStatus.PLACED);
    }

    @Test
    void customerCannotCancelOnceTheRestaurantAccepted() {
        when(orderRepository.findByIdAndCustomerId(500L, CUSTOMER))
                .thenReturn(Optional.of(order(500L, CUSTOMER, 10L, OrderStatus.ACCEPTED)));

        assertThatThrownBy(() -> customerOrderService.cancelMyOrder(CUSTOMER, 500L, "Changed my mind"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("contact support");
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void customerCannotSeeSomeoneElsesOrder() {
        when(orderRepository.findByIdAndCustomerId(500L, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerOrderService.getMyOrder(CUSTOMER, 500L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminCanCancelAnOrderInPreparationAndItIsAudited() {
        Order order = order(500L, CUSTOMER, 10L, OrderStatus.PREPARING);
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

        OrderResponse response = adminOrderService.cancelOrder(500L, "Customer support: duplicate order", ADMIN);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelledBy()).isEqualTo(ActorType.ADMIN);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.ORDER_CANCELLED_BY_ADMIN, "ORDER", 500L,
                "PREPARING", "CANCELLED", "Customer support: duplicate order");
        verify(orderEventPublisher).orderCancelled(order, OrderStatus.PREPARING);
    }

    @Test
    void deliveredOrderCannotBeCancelled() {
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order(500L, CUSTOMER, 10L, OrderStatus.DELIVERED)));

        assertThatThrownBy(() -> adminOrderService.cancelOrder(500L, "Too late", ADMIN))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(adminAuditService, orderEventPublisher);
    }
}
