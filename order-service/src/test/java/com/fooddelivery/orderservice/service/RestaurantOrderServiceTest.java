package com.fooddelivery.orderservice.service;

import static com.fooddelivery.orderservice.service.OrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.orderservice.client.RemoteRecords.RestaurantSummary;
import com.fooddelivery.orderservice.client.RestaurantServiceClient;
import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.RestaurantTopItemsResponse;
import com.fooddelivery.orderservice.dto.response.SalesSummaryResponse;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.exception.ConflictException;
import com.fooddelivery.orderservice.exception.InvalidRequestException;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.exception.RestaurantNotFoundException;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.DayTotals;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.HourTotals;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import com.fooddelivery.orderservice.repository.StatusTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderServiceTest {

    private static final Long OWNER = 3L;
    private static final Long RESTAURANT = 10L;

    @Mock
    private RestaurantServiceClient restaurantServiceClient;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderTimelineRepository orderTimelineRepository;
    @Mock
    private OrderStatsRepository orderStatsRepository;
    @Mock
    private OrderEventPublisher orderEventPublisher;

    private RestaurantOrderService restaurantOrderService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T07:00:00Z"), ZoneOffset.UTC);
        OrderStatusTransitions transitions = new OrderStatusTransitions(orderTimelineRepository, orderEventPublisher,
                new OrderMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), clock);
        restaurantOrderService = new RestaurantOrderService(restaurantServiceClient, orderRepository, orderStatsRepository,
                new OrderStatsService(orderStatsRepository, TestProperties.appProperties(), clock),
                orderTimelineRepository, transitions, new OrderMapper(), TestProperties.appProperties(), clock,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void restaurantAcceptsANewOrder() {
        givenOwnedRestaurant();
        givenOrder(OrderStatus.PLACED);

        OrderResponse response = restaurantOrderService.acceptOrder(OWNER, RESTAURANT, 500L);

        assertThat(response.status()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void preparingCannotStartBeforeAccepting() {
        givenOwnedRestaurant();
        givenOrder(OrderStatus.PLACED);

        assertThatThrownBy(() -> restaurantOrderService.markPreparing(OWNER, RESTAURANT, 500L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("from PLACED to PREPARING");
    }

    @Test
    void rejectionKeepsTheReasonForTheCustomer() {
        givenOwnedRestaurant();
        Order order = givenOrder(OrderStatus.PLACED);

        OrderResponse response = restaurantOrderService.rejectOrder(OWNER, RESTAURANT, 500L, "Out of chicken today");

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.cancellationReason()).isEqualTo("Out of chicken today");
        assertThat(response.cancelledBy()).isEqualTo(ActorType.RESTAURANT);
        verify(orderEventPublisher).orderCancelled(order, OrderStatus.PLACED);
    }

    @Test
    void unpaidOnlineOrdersAreInvisibleToTheRestaurant() {
        givenOwnedRestaurant();
        givenOrder(OrderStatus.PAYMENT_PENDING);

        assertThatThrownBy(() -> restaurantOrderService.acceptOrder(OWNER, RESTAURANT, 500L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anotherOwnerCannotHandleTheOrders() {
        when(restaurantServiceClient.findRestaurant(RESTAURANT))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT, 999L, "Spice Hub", "ACTIVE", true)));

        assertThatThrownBy(() -> restaurantOrderService.acceptOrder(OWNER, RESTAURANT, 500L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void salesSummaryCountsAcceptedAndDeliveredValueSeparately() {
        givenOwnedRestaurant();
        when(orderStatsRepository.sumPerDayAndStatusForRestaurant(eq(RESTAURANT), any(), any(), any())).thenReturn(List.of(
                day("2026-10-07", "DELIVERED", 3, "900.00"), day("2026-10-07", "REJECTED", 2, "400.00")));
        when(orderStatsRepository.sumPerHourAndStatusForRestaurant(eq(RESTAURANT), any(), any(), any())).thenReturn(
                List.of(hour(12, "DELIVERED", 3, "900.00"), hour(19, "REJECTED", 2, "400.00")));
        when(orderRepository.sumByStatusForRestaurant(org.mockito.ArgumentMatchers.eq(RESTAURANT),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        totals(OrderStatus.DELIVERED, 3, "900.00"),
                        totals(OrderStatus.PREPARING, 1, "250.00"),
                        totals(OrderStatus.REJECTED, 2, "400.00"),
                        totals(OrderStatus.PAYMENT_PENDING, 5, "999.00")));

        SalesSummaryResponse summary = restaurantOrderService.getSalesSummary(OWNER, RESTAURANT, null, null);

        assertThat(summary.fromDate()).isEqualTo(LocalDate.of(2026, 10, 7));    // default: today in India
        assertThat(summary.totalOrders()).isEqualTo(6);                          // unpaid orders are not sales
        assertThat(summary.acceptedOrdersValue()).isEqualByComparingTo("1150.00");
        assertThat(summary.deliveredOrdersValue()).isEqualByComparingTo("900.00");
        // The day of the period, from the restaurant's own per-day rows (unpaid orders are not asked for)
        assertThat(summary.perDay()).singleElement().satisfies(day -> {
            assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 7));
            assertThat(day.orders()).isEqualTo(5);
            assertThat(day.completed()).isEqualTo(3);
            assertThat(day.cancelled()).isEqualTo(2);
            assertThat(day.revenue()).isEqualByComparingTo("900.00");
        });
        // Every hour of the day, with the restaurant's own per-hour rows
        assertThat(summary.perHour()).hasSize(24);
        assertThat(summary.perHour().get(12))
                .isEqualTo(new SalesSummaryResponse.Hour(12, 3, 3, 0, new BigDecimal("900.00")));
        assertThat(summary.perHour().get(19))
                .isEqualTo(new SalesSummaryResponse.Hour(19, 2, 0, 2, new BigDecimal("0.00")));
    }

    @Test
    void topItemsDefaultToTodayInTheBusinessZoneAndRoundRevenueToTwoDecimals() {
        givenOwnedRestaurant();
        when(orderStatsRepository.topItemsOfRestaurant(eq(RESTAURANT), any(), any(), eq(5))).thenReturn(List.of(
                item(100L, "Chicken Biryani", 7, 4, "2093"), item(101L, "Malai Kulfi", 2, 2, "178.5")));

        RestaurantTopItemsResponse response = restaurantOrderService.getTopItems(OWNER, RESTAURANT, null, null, 5);

        assertThat(response.restaurantId()).isEqualTo(RESTAURANT);
        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.to()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.items()).extracting(RestaurantTopItemsResponse.Item::menuItemId).containsExactly(100L, 101L);
        assertThat(response.items().getFirst().revenue()).isEqualTo(new BigDecimal("2093.00"));
        assertThat(response.items().get(1).revenue()).isEqualTo(new BigDecimal("178.50"));
        assertThat(response.items().getFirst().quantity()).isEqualTo(7);
        assertThat(response.items().getFirst().orders()).isEqualTo(4);
        // today in India: 7 October 00:00 IST (6 October 18:30 UTC) up to, not including, 8 October 00:00 IST
        verify(orderStatsRepository).topItemsOfRestaurant(RESTAURANT, Instant.parse("2026-10-06T18:30:00Z"),
                Instant.parse("2026-10-07T18:30:00Z"), 5);
    }

    @Test
    void topItemsIncludeBothEndDatesOfTheRange() {
        givenOwnedRestaurant();
        when(orderStatsRepository.topItemsOfRestaurant(eq(RESTAURANT), any(), any(), eq(3))).thenReturn(List.of());

        RestaurantTopItemsResponse response = restaurantOrderService.getTopItems(OWNER, RESTAURANT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 3);

        assertThat(response.items()).isEmpty();
        verify(orderStatsRepository).topItemsOfRestaurant(RESTAURANT, Instant.parse("2026-09-30T18:30:00Z"),
                Instant.parse("2026-10-03T18:30:00Z"), 3);
    }

    @Test
    void topItemsRangeIsAtMost366DaysAndMustNotBeReversed() {
        givenOwnedRestaurant();
        when(orderStatsRepository.topItemsOfRestaurant(eq(RESTAURANT), any(), any(), eq(5))).thenReturn(List.of());

        restaurantOrderService.getTopItems(OWNER, RESTAURANT, LocalDate.of(2025, 10, 7), LocalDate.of(2026, 10, 7), 5);

        assertThatThrownBy(() -> restaurantOrderService.getTopItems(OWNER, RESTAURANT,
                LocalDate.of(2025, 10, 6), LocalDate.of(2026, 10, 7), 5)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> restaurantOrderService.getTopItems(OWNER, RESTAURANT,
                LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 6), 5)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void topItemsOfAnotherOwnersRestaurantAreRefusedWithoutQuerying() {
        when(restaurantServiceClient.findRestaurant(RESTAURANT))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT, 999L, "Spice Hub", "ACTIVE", true)));

        assertThatThrownBy(() -> restaurantOrderService.getTopItems(OWNER, RESTAURANT, null, null, 5))
                .isInstanceOf(RestaurantNotFoundException.class);
        verifyNoInteractions(orderStatsRepository);
    }

    private static OrderStatsRepository.RestaurantItemTotals item(long id, String name, long quantity, long orders,
                                                                  String revenue) {
        return new OrderStatsRepository.RestaurantItemTotals() {
            public long getMenuItemId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public long getQuantity() {
                return quantity;
            }

            public long getOrderCount() {
                return orders;
            }

            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }
        };
    }

    private void givenOwnedRestaurant() {
        when(restaurantServiceClient.findRestaurant(RESTAURANT))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT, OWNER, "Spice Hub", "ACTIVE", true)));
    }

    private Order givenOrder(OrderStatus status) {
        Order order = order(500L, 7L, RESTAURANT, status);
        when(orderRepository.findByIdAndRestaurantId(500L, RESTAURANT)).thenReturn(Optional.of(order));
        return order;
    }

    private static StatusTotals totals(OrderStatus status, long count, String amount) {
        return new StatusTotals() {
            public OrderStatus getStatus() {
                return status;
            }

            public long getOrderCount() {
                return count;
            }

            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private static HourTotals hour(int hour, String status, long orders, String amount) {
        return new HourTotals() {
            @Override
            public int getHour() {
                return hour;
            }

            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public long getOrderCount() {
                return orders;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private static DayTotals day(String day, String status, long orders, String amount) {
        return new DayTotals() {
            @Override
            public String getDay() {
                return day;
            }

            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public long getOrderCount() {
                return orders;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }
        };
    }
}
