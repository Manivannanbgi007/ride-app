package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.client.RemoteRecords.RestaurantSummary;
import com.fooddelivery.orderservice.client.RestaurantServiceClient;
import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.request.OrderSearchCriteria;
import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.OrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.PageResponse;
import com.fooddelivery.orderservice.dto.response.RestaurantTopItemsResponse;
import com.fooddelivery.orderservice.dto.response.SalesSummaryResponse;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.exception.InvalidRequestException;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.exception.RestaurantNotFoundException;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderSpecifications;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import com.fooddelivery.orderservice.repository.StatusTotals;
import com.fooddelivery.orderservice.validation.SortValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A restaurant owner handling incoming orders: accept or reject, then preparing and ready for pickup.
 * Orders still waiting for online payment are not shown: the restaurant only sees orders it can cook.
 * <p>
 * Ownership is checked with restaurant-service first, then the database work runs in a short transaction.
 */
@Service
@RequiredArgsConstructor
public class RestaurantOrderService {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "totalAmount", "status");
    private static final int MAX_SUMMARY_DAYS = 366;

    private final RestaurantServiceClient restaurantServiceClient;
    private final OrderRepository orderRepository;
    private final OrderStatsRepository orderStatsRepository;
    private final OrderStatsService orderStatsService;
    private final OrderTimelineRepository orderTimelineRepository;
    private final OrderStatusTransitions orderStatusTransitions;
    private final OrderMapper orderMapper;
    private final AppProperties appProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PageResponse<OrderSummaryResponse> getOrders(Long ownerUserId, Long restaurantId,
                                                        OrderSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        requireOwnedRestaurant(restaurantId, ownerUserId);
        BusinessDateRange range = BusinessDateRange.of(criteria.fromDate(), criteria.toDate(), businessZone());

        List<Specification<Order>> conditions = new ArrayList<>();
        conditions.add(OrderSpecifications.ofRestaurant(restaurantId));
        conditions.add(OrderSpecifications.statusIsNot(OrderStatus.PAYMENT_PENDING));
        if (criteria.status() != null) {
            conditions.add(OrderSpecifications.hasStatus(criteria.status()));
        }
        if (range.from() != null) {
            conditions.add(OrderSpecifications.createdFrom(range.from()));
        }
        if (range.before() != null) {
            conditions.add(OrderSpecifications.createdBefore(range.before()));
        }

        return transactionTemplate.execute(transaction -> PageResponse.from(
                orderRepository.findAll(Specification.allOf(conditions), pageable).map(orderMapper::toSummary)));
    }

    public OrderResponse getOrder(Long ownerUserId, Long restaurantId, Long orderId) {
        requireOwnedRestaurant(restaurantId, ownerUserId);
        return transactionTemplate.execute(transaction -> toResponse(findRestaurantOrder(restaurantId, orderId)));
    }

    public OrderResponse acceptOrder(Long ownerUserId, Long restaurantId, Long orderId) {
        return changeStatus(ownerUserId, restaurantId, orderId, OrderStatus.ACCEPTED, "Accepted by the restaurant");
    }

    public OrderResponse markPreparing(Long ownerUserId, Long restaurantId, Long orderId) {
        return changeStatus(ownerUserId, restaurantId, orderId, OrderStatus.PREPARING, "Food is being prepared");
    }

    public OrderResponse markReadyForPickup(Long ownerUserId, Long restaurantId, Long orderId) {
        return changeStatus(ownerUserId, restaurantId, orderId, OrderStatus.READY_FOR_PICKUP, "Food is ready for pickup");
    }

    /** Rejecting is like cancelling, with the restaurant as the actor. Refunds of paid orders follow in Phase 6. */
    public OrderResponse rejectOrder(Long ownerUserId, Long restaurantId, Long orderId, String reason) {
        requireOwnedRestaurant(restaurantId, ownerUserId);
        return transactionTemplate.execute(transaction -> {
            Order order = findRestaurantOrder(restaurantId, orderId);
            orderStatusTransitions.cancel(order, OrderStatus.REJECTED, ActorType.RESTAURANT, ownerUserId, reason);
            orderRepository.flush();
            return toResponse(order);
        });
    }

    /** Sales of a period (default: today). Counted and summed in the database. */
    public SalesSummaryResponse getSalesSummary(Long ownerUserId, Long restaurantId, LocalDate fromDate, LocalDate toDate) {
        requireOwnedRestaurant(restaurantId, ownerUserId);

        LocalDate today = LocalDate.now(clock.withZone(businessZone()));
        LocalDate from = fromDate != null ? fromDate : today;
        LocalDate to = toDate != null ? toDate : today;
        if (ChronoUnit.DAYS.between(from, to) >= MAX_SUMMARY_DAYS) {
            throw new InvalidRequestException("The period can be at most " + MAX_SUMMARY_DAYS + " days");
        }
        BusinessDateRange range = BusinessDateRange.of(from, to, businessZone());

        List<StatusTotals> totals = orderRepository.sumByStatusForRestaurant(restaurantId, range.from(), range.before());

        Map<OrderStatus, Long> ordersByStatus = new EnumMap<>(OrderStatus.class);
        BigDecimal acceptedValue = BigDecimal.ZERO;
        BigDecimal deliveredValue = BigDecimal.ZERO;
        long totalOrders = 0;
        for (StatusTotals total : totals) {
            if (total.getStatus() == OrderStatus.PAYMENT_PENDING) {
                continue;
            }
            ordersByStatus.put(total.getStatus(), total.getOrderCount());
            totalOrders += total.getOrderCount();
            if (total.getStatus().isAcceptedByRestaurant()) {
                acceptedValue = acceptedValue.add(total.getTotalAmount());
            }
            if (total.getStatus() == OrderStatus.DELIVERED) {
                deliveredValue = deliveredValue.add(total.getTotalAmount());
            }
        }

        return new SalesSummaryResponse(restaurantId, from, to, totalOrders, ordersByStatus,
                acceptedValue.setScale(2), deliveredValue.setScale(2), appProperties.pricing().currency(),
                orderStatsService.perDayForRestaurant(restaurantId, from, to),
                orderStatsService.perHourForRestaurant(restaurantId, from, to));
    }

    /**
     * The most ordered dishes of the restaurant in a period (default: today). Counts the same orders as the
     * sales summary: every status except PAYMENT_PENDING. One grouped query, ranked in the database.
     */
    public RestaurantTopItemsResponse getTopItems(Long ownerUserId, Long restaurantId, LocalDate fromDate,
                                                  LocalDate toDate, int limit) {
        requireOwnedRestaurant(restaurantId, ownerUserId);

        LocalDate today = LocalDate.now(clock.withZone(businessZone()));
        LocalDate from = fromDate != null ? fromDate : today;
        LocalDate to = toDate != null ? toDate : today;
        if (ChronoUnit.DAYS.between(from, to) >= MAX_SUMMARY_DAYS) {
            throw new InvalidRequestException("The period can be at most " + MAX_SUMMARY_DAYS + " days");
        }
        BusinessDateRange range = BusinessDateRange.of(from, to, businessZone());

        List<RestaurantTopItemsResponse.Item> items = orderStatsRepository
                .topItemsOfRestaurant(restaurantId, range.from(), range.before(), limit).stream()
                .map(row -> new RestaurantTopItemsResponse.Item(row.getMenuItemId(), row.getName(), row.getQuantity(),
                        row.getOrderCount(), row.getRevenue().setScale(2, RoundingMode.HALF_UP)))
                .toList();
        return new RestaurantTopItemsResponse(restaurantId, from, to, items);
    }

    private OrderResponse changeStatus(Long ownerUserId, Long restaurantId, Long orderId, OrderStatus newStatus,
                                       String note) {
        requireOwnedRestaurant(restaurantId, ownerUserId);
        return transactionTemplate.execute(transaction -> {
            Order order = findRestaurantOrder(restaurantId, orderId);
            orderStatusTransitions.changeStatus(order, newStatus, ActorType.RESTAURANT, ownerUserId, note);
            orderRepository.flush();
            return toResponse(order);
        });
    }

    private void requireOwnedRestaurant(Long restaurantId, Long ownerUserId) {
        restaurantServiceClient.findRestaurant(restaurantId)
                .filter(restaurant -> ownerUserId.equals(restaurant.ownerUserId()))
                .map(RestaurantSummary::id)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }

    private Order findRestaurantOrder(Long restaurantId, Long orderId) {
        return orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .filter(order -> order.getStatus() != OrderStatus.PAYMENT_PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private OrderResponse toResponse(Order order) {
        return orderMapper.toResponse(order, orderTimelineRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
    }

    private ZoneId businessZone() {
        return ZoneId.of(appProperties.businessTimeZone());
    }
}
