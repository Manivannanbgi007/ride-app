package com.fooddelivery.orderservice.service;

import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.OrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.PageResponse;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.exception.ConflictException;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderSpecifications;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import com.fooddelivery.orderservice.validation.SortValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A customer looking at, tracking and cancelling their own orders. */
@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "totalAmount");

    private final OrderRepository orderRepository;
    private final OrderTimelineRepository orderTimelineRepository;
    private final OrderStatusTransitions orderStatusTransitions;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(Long customerId, OrderStatus status, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);

        List<Specification<Order>> conditions = new ArrayList<>();
        conditions.add(OrderSpecifications.ofCustomer(customerId));
        if (status != null) {
            conditions.add(OrderSpecifications.hasStatus(status));
        }
        return PageResponse.from(orderRepository.findAll(Specification.allOf(conditions), pageable)
                .map(orderMapper::toSummary));
    }

    /** Order tracking: current status plus the full timeline. */
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long customerId, Long orderId) {
        return toResponse(findMyOrder(customerId, orderId));
    }

    /** Allowed only before the restaurant accepts the order. Refunds of paid orders follow in Phase 6. */
    @Transactional
    public OrderResponse cancelMyOrder(Long customerId, Long orderId, String reason) {
        Order order = findMyOrder(customerId, orderId);
        if (!order.getStatus().isCancellableByCustomer()) {
            throw new ConflictException("This order can no longer be cancelled (status " + order.getStatus()
                    + "). Please contact support.");
        }

        orderStatusTransitions.cancel(order, OrderStatus.CANCELLED, ActorType.CUSTOMER, customerId, reason);
        orderRepository.flush();
        return toResponse(order);
    }

    private Order findMyOrder(Long customerId, Long orderId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private OrderResponse toResponse(Order order) {
        return orderMapper.toResponse(order, orderTimelineRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
    }
}
