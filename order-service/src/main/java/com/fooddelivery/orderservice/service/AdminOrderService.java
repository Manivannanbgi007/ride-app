package com.fooddelivery.orderservice.service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.request.OrderSearchCriteria;
import com.fooddelivery.orderservice.dto.response.AuditLogResponse;
import com.fooddelivery.orderservice.dto.response.CustomerOrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.OrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.PageResponse;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.AuditAction;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.exception.ConflictException;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderSpecifications;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import com.fooddelivery.orderservice.validation.SortValidator;
import com.fooddelivery.orderservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform admins: full visibility of orders, and audited interventions. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "updatedAt", "totalAmount", "status");

    private final OrderRepository orderRepository;
    private final OrderTimelineRepository orderTimelineRepository;
    private final OrderStatusTransitions orderStatusTransitions;
    private final AdminAuditService adminAuditService;
    private final OrderMapper orderMapper;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> searchOrders(OrderSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        BusinessDateRange range = BusinessDateRange.of(criteria.fromDate(), criteria.toDate(),
                ZoneId.of(appProperties.businessTimeZone()));

        List<Specification<Order>> conditions = new ArrayList<>();
        if (criteria.orderId() != null) {
            conditions.add(OrderSpecifications.hasId(criteria.orderId()));
        }
        if (criteria.customerId() != null) {
            conditions.add(OrderSpecifications.ofCustomer(criteria.customerId()));
        }
        if (criteria.restaurantId() != null) {
            conditions.add(OrderSpecifications.ofRestaurant(criteria.restaurantId()));
        }
        if (criteria.status() != null) {
            conditions.add(OrderSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.paymentStatus() != null) {
            conditions.add(OrderSpecifications.hasPaymentStatus(criteria.paymentStatus()));
        }
        if (range.from() != null) {
            conditions.add(OrderSpecifications.createdFrom(range.from()));
        }
        if (range.before() != null) {
            conditions.add(OrderSpecifications.createdBefore(range.before()));
        }

        return PageResponse.from(orderRepository.findAll(Specification.allOf(conditions), pageable)
                .map(orderMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return toResponse(findOrder(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse.TimelineEntry> getTimeline(Long orderId) {
        return toResponse(findOrder(orderId)).timeline();
    }

    /**
     * Intervention: cancel any order that is not finished yet. Audited.
     * (When payments exist, a refund for paid orders is started from here: Phase 6.)
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason, CallerInfo admin) {
        Order order = findOrder(orderId);
        OrderStatus oldStatus = order.getStatus();
        if (!oldStatus.canChangeTo(OrderStatus.CANCELLED)) {
            throw new ConflictException("Order " + orderId + " is " + oldStatus + " and can no longer be cancelled");
        }

        orderStatusTransitions.cancel(order, OrderStatus.CANCELLED, ActorType.ADMIN, admin.userId(), reason);
        adminAuditService.recordAction(admin, AuditAction.ORDER_CANCELLED_BY_ADMIN, AdminAuditService.ENTITY_TYPE_ORDER,
                orderId, oldStatus.name(), OrderStatus.CANCELLED.name(), reason);
        orderRepository.flush();
        return toResponse(order);
    }

    /** The "user order summary" on the admin user screen. */
    @Transactional(readOnly = true)
    public CustomerOrderSummaryResponse getCustomerSummary(Long customerId) {
        return CustomerSummaries.of(customerId, orderRepository.sumByStatusForCustomer(customerId),
                orderRepository.findLastOrderTime(customerId).orElse(null), appProperties.pricing().currency());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getOrderAuditLogs(Long orderId, Pageable pageable) {
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_ORDER, orderId, pageable);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private OrderResponse toResponse(Order order) {
        return orderMapper.toResponse(order, orderTimelineRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
    }
}
