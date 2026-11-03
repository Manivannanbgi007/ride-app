package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.orderservice.client.CatalogServiceClient;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemCard;
import com.fooddelivery.orderservice.dto.response.CustomerOrderActivityResponse;
import com.fooddelivery.orderservice.dto.response.CustomerOrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.FavouriteDishResponse;
import com.fooddelivery.orderservice.dto.response.OrderStatsResponse;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.exception.InvalidRequestException;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.DayTotals;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.FavouriteDishRow;
import com.fooddelivery.orderservice.repository.StatusTotals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerInsightsServiceTest {

    // Wednesday 2026-10-07 10:00 in Asia/Kolkata (the business zone of TestProperties)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T04:30:00Z"), ZoneOffset.UTC);
    private static final long CUSTOMER = 7L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatsRepository orderStatsRepository;
    @Mock
    private CatalogServiceClient catalogServiceClient;

    private CustomerInsightsService service() {
        OrderStatsService stats = new OrderStatsService(orderStatsRepository, TestProperties.appProperties(), CLOCK);
        return new CustomerInsightsService(orderRepository, orderStatsRepository, stats, catalogServiceClient,
                TestProperties.appProperties(), CLOCK);
    }

    @Test
    void theSummaryCountsEveryOrderAndSpendingIsTheDeliveredOrdersOnly() {
        when(orderRepository.sumByStatusForCustomer(CUSTOMER)).thenReturn(List.of(
                totals(OrderStatus.DELIVERED, 3, "900.00"), totals(OrderStatus.PREPARING, 1, "250.00"),
                totals(OrderStatus.PAYMENT_PENDING, 1, "120.00"), totals(OrderStatus.CANCELLED, 2, "400.00"),
                totals(OrderStatus.REJECTED, 1, "99.00")));
        Instant last = Instant.parse("2026-10-06T12:00:00Z");
        when(orderRepository.findLastOrderTime(CUSTOMER)).thenReturn(Optional.of(last));

        CustomerOrderSummaryResponse summary = service().summary(CUSTOMER);

        assertThat(summary.customerId()).isEqualTo(CUSTOMER);
        assertThat(summary.totalOrders()).isEqualTo(8);
        // Not finished yet: being prepared, and waiting for payment
        assertThat(summary.activeOrders()).isEqualTo(2);
        assertThat(summary.deliveredOrders()).isEqualTo(3);
        assertThat(summary.cancelledOrders()).isEqualTo(2);
        assertThat(summary.rejectedOrders()).isEqualTo(1);
        assertThat(summary.totalSpent()).isEqualByComparingTo("900.00");
        assertThat(summary.lastOrderAt()).isEqualTo(last);
        assertThat(summary.currency()).isEqualTo("INR");
    }

    @Test
    void aCustomerWithoutOrdersHasZerosAndNoLastOrder() {
        when(orderRepository.sumByStatusForCustomer(CUSTOMER)).thenReturn(List.of());
        when(orderRepository.findLastOrderTime(CUSTOMER)).thenReturn(Optional.empty());

        CustomerOrderSummaryResponse summary = service().summary(CUSTOMER);

        assertThat(summary.totalOrders()).isZero();
        assertThat(summary.totalSpent()).isEqualByComparingTo("0.00");
        assertThat(summary.lastOrderAt()).isNull();
    }

    @Test
    void theActivityCoversTheBusinessDaysOfThePeriodWithEveryDayListed() {
        // 1 to 7 Oct in Kolkata = [30 Sep 18:30, 7 Oct 18:30) UTC
        Instant from = Instant.parse("2026-09-30T18:30:00Z");
        Instant before = Instant.parse("2026-10-07T18:30:00Z");
        when(orderRepository.sumByStatusForCustomerBetween(CUSTOMER, from, before)).thenReturn(List.of(
                totals(OrderStatus.DELIVERED, 2, "540.50"), totals(OrderStatus.CANCELLED, 1, "120.00"),
                totals(OrderStatus.OUT_FOR_DELIVERY, 1, "300.00")));
        when(orderStatsRepository.sumPerDayAndStatusForCustomer(eq(CUSTOMER), eq(from), eq(before), anyString()))
                .thenReturn(List.of(day("2026-10-03", "DELIVERED", 2, "540.50"),
                        day("2026-10-05", "CANCELLED", 1, "120.00"), day("2026-10-07", "OUT_FOR_DELIVERY", 1, "300.00")));

        CustomerOrderActivityResponse activity = service().activity(CUSTOMER, LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-07"));

        assertThat(activity.fromDate()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(activity.toDate()).isEqualTo(LocalDate.parse("2026-10-07"));
        assertThat(activity.totalOrders()).isEqualTo(4);
        assertThat(activity.ordersByStatus())
                .containsEntry("DELIVERED", 2L).containsEntry("CANCELLED", 1L).containsEntry("OUT_FOR_DELIVERY", 1L);
        // Only what was delivered was spent
        assertThat(activity.spent()).isEqualByComparingTo("540.50");
        assertThat(activity.perDay()).hasSize(7);
        OrderStatsResponse.Day third = activity.perDay().get(2);
        assertThat(third.date()).isEqualTo(LocalDate.parse("2026-10-03"));
        assertThat(third.orders()).isEqualTo(2);
        assertThat(third.completed()).isEqualTo(2);
        assertThat(third.revenue()).isEqualByComparingTo("540.50");
        assertThat(activity.perDay().get(4).cancelled()).isEqualTo(1);
        assertThat(activity.perDay().get(1).orders()).isZero();
    }

    @Test
    void theActivityPeriodMustMakeSense() {
        assertThatThrownBy(() -> service().activity(CUSTOMER, LocalDate.parse("2026-10-07"),
                LocalDate.parse("2026-10-01")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service().activity(CUSTOMER, LocalDate.parse("2025-01-01"),
                LocalDate.parse("2026-10-07")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("366");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void favouriteDishesGetTodaysPriceAndStockAndAreNeverOfferedWithoutThem() {
        when(orderStatsRepository.favouriteDishesOfCustomer(CUSTOMER, 6)).thenReturn(List.of(
                dish(11L, "Chicken Biryani", 4, 6), dish(12L, "Paneer Tikka", 2, 2), dish(13L, "Old Special", 1, 1)));
        when(catalogServiceClient.findMenuItemCards(anyCollection())).thenReturn(Map.of(
                11L, card(11L, "Chicken Biryani", "349.00", "ACTIVE", true),
                12L, card(12L, "Paneer Tikka", "180.00", "ACTIVE", false)));

        List<FavouriteDishResponse> dishes = service().favouriteDishes(CUSTOMER, 6);

        assertThat(dishes).extracting(FavouriteDishResponse::menuItemId).containsExactly(11L, 12L, 13L);
        FavouriteDishResponse biryani = dishes.getFirst();
        // Today's price, not the one it was ordered at
        assertThat(biryani.price()).isEqualByComparingTo("349.00");
        assertThat(biryani.available()).isTrue();
        assertThat(biryani.imageUrl()).isEqualTo("http://img/11.jpg");
        assertThat(biryani.restaurantName()).isEqualTo("Spice Hub");
        assertThat(biryani.timesOrdered()).isEqualTo(4);
        // Sold out today: shown, but cannot be added
        assertThat(dishes.get(1).available()).isFalse();
        assertThat(dishes.get(1).price()).isEqualByComparingTo("180.00");
        // No longer on any menu: its name from the order, no price, not available
        FavouriteDishResponse gone = dishes.get(2);
        assertThat(gone.name()).isEqualTo("Old Special");
        assertThat(gone.price()).isNull();
        assertThat(gone.available()).isFalse();
        verify(catalogServiceClient).findMenuItemCards(List.of(11L, 12L, 13L));
    }

    @Test
    void favouriteDishesFailWhenTheCatalogCannotConfirmPrices() {
        when(orderStatsRepository.favouriteDishesOfCustomer(CUSTOMER, 6)).thenReturn(List.of(dish(11L, "Biryani", 1, 1)));
        when(catalogServiceClient.findMenuItemCards(any()))
                .thenThrow(new RemoteServiceException("catalog-service", new IllegalStateException("down")));

        assertThatThrownBy(() -> service().favouriteDishes(CUSTOMER, 6)).isInstanceOf(RemoteServiceException.class);
    }

    @Test
    void aCustomerWhoNeverOrderedHasNoFavouritesAndTheCatalogIsNotAsked() {
        when(orderStatsRepository.favouriteDishesOfCustomer(CUSTOMER, 6)).thenReturn(List.of());
        when(catalogServiceClient.findMenuItemCards(List.of())).thenReturn(Map.of());

        assertThat(service().favouriteDishes(CUSTOMER, 6)).isEmpty();
    }

    private record Totals(OrderStatus status, long count, BigDecimal amount) implements StatusTotals {
        @Override
        public OrderStatus getStatus() {
            return status;
        }

        @Override
        public long getOrderCount() {
            return count;
        }

        @Override
        public BigDecimal getTotalAmount() {
            return amount;
        }
    }

    private record Day(String date, String status, long count, BigDecimal amount) implements DayTotals {
        @Override
        public String getDay() {
            return date;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public long getOrderCount() {
            return count;
        }

        @Override
        public BigDecimal getTotalAmount() {
            return amount;
        }
    }

    private record Dish(long id, String name, long orders, long quantity) implements FavouriteDishRow {
        @Override
        public long getMenuItemId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getRestaurantId() {
            return 3L;
        }

        @Override
        public String getRestaurantName() {
            return "Spice Hub";
        }

        @Override
        public long getOrderCount() {
            return orders;
        }

        @Override
        public long getQuantity() {
            return quantity;
        }

        @Override
        public Instant getLastOrderedAt() {
            return Instant.parse("2026-10-01T10:00:00Z");
        }
    }

    private static StatusTotals totals(OrderStatus status, long count, String amount) {
        return new Totals(status, count, new BigDecimal(amount));
    }

    private static DayTotals day(String date, String status, long count, String amount) {
        return new Day(date, status, count, new BigDecimal(amount));
    }

    private static FavouriteDishRow dish(long id, String name, long orders, long quantity) {
        return new Dish(id, name, orders, quantity);
    }

    private static MenuItemCard card(long id, String name, String price, String status, boolean available) {
        return new MenuItemCard(id, 3L, name, new BigDecimal(price), "NON_VEG", status, available,
                "http://img/" + id + ".jpg", "Mains");
    }
}
