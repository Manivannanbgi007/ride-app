package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.orderservice.dto.response.MenuItemRevenueResponse;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.DishDayTotals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuItemRevenueServiceTest {

    // Wednesday 2026-10-07 10:00 in Asia/Kolkata (the business zone of TestProperties)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T04:30:00Z"), ZoneOffset.UTC);

    @Mock
    private OrderStatsRepository orderStatsRepository;

    private MenuItemRevenueService service() {
        return new MenuItemRevenueService(orderStatsRepository, TestProperties.appProperties(), CLOCK);
    }

    @Test
    void monthsAreTwelveIncludingTheCurrentOneAndEmptyOnesAreZero() {
        when(orderStatsRepository.sumDishPerDay(eq(9L), eq("DELIVERED"), any(), any(), eq("+05:30"))).thenReturn(List.of(
                row("2026-10-01", "200.00", 2, 2), row("2026-10-05", "100.00", 1, 1), row("2026-08-31", "50.00", 1, 1)));

        MenuItemRevenueResponse response = service().getRevenue(9L, RevenuePeriod.MONTHLY);

        assertThat(response.points()).hasSize(12);
        assertThat(response.points().getFirst().label()).isEqualTo("Nov 2025");
        assertThat(response.points().getFirst().start()).isEqualTo(LocalDate.parse("2025-11-01"));
        assertThat(response.points().getLast().label()).isEqualTo("Oct 2026");
        assertThat(response.points().getLast().revenue()).isEqualByComparingTo("300.00");
        assertThat(response.points().getLast().quantity()).isEqualTo(3);
        assertThat(response.points().getLast().orders()).isEqualTo(3);
        assertThat(response.points().get(10).label()).isEqualTo("Sep 2026");
        assertThat(response.points().get(10).revenue()).isEqualByComparingTo("0.00");
        assertThat(response.points().get(9).revenue()).isEqualByComparingTo("50.00");
        assertThat(response.totalRevenue()).isEqualByComparingTo("350.00");
        assertThat(response.totalQuantity()).isEqualTo(4);
    }

    @Test
    void weeksStartOnMondayAndAreLabelledByTheirFirstDay() {
        when(orderStatsRepository.sumDishPerDay(eq(9L), anyString(), any(), any(), anyString())).thenReturn(List.of(
                row("2026-10-04", "10.00", 1, 1), row("2026-10-05", "20.00", 1, 1), row("2026-10-07", "30.00", 2, 1)));

        MenuItemRevenueResponse response = service().getRevenue(9L, RevenuePeriod.WEEKLY);

        assertThat(response.points()).hasSize(12);
        assertThat(response.points().getLast().label()).isEqualTo("5 Oct");
        assertThat(response.points().getLast().start()).isEqualTo(LocalDate.parse("2026-10-05"));
        assertThat(response.points().getLast().revenue()).isEqualByComparingTo("50.00");
        assertThat(response.points().getLast().quantity()).isEqualTo(3);
        assertThat(response.points().get(10).label()).isEqualTo("28 Sep"); // Sunday 4 Oct belongs to the week before
        assertThat(response.points().get(10).revenue()).isEqualByComparingTo("10.00");
        assertThat(response.points().getFirst().start()).isEqualTo(LocalDate.parse("2026-07-20"));
    }

    @Test
    void daysAreThirtyEndingTodayAndOneQueryCoversTheWindow() {
        when(orderStatsRepository.sumDishPerDay(eq(9L), anyString(), any(), any(), anyString())).thenReturn(List.of());

        MenuItemRevenueResponse response = service().getRevenue(9L, RevenuePeriod.DAILY);

        assertThat(response.points()).hasSize(30);
        assertThat(response.points().getFirst().start()).isEqualTo(LocalDate.parse("2026-09-08"));
        assertThat(response.points().getFirst().label()).isEqualTo("8 Sep");
        assertThat(response.points().getLast().label()).isEqualTo("7 Oct");
        assertThat(response.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.totalQuantity()).isZero();
        verify(orderStatsRepository).sumDishPerDay(9L, "DELIVERED", Instant.parse("2026-09-07T18:30:00Z"),
                Instant.parse("2026-10-07T18:30:00Z"), "+05:30");
    }

    private static DishDayTotals row(String day, String revenue, long quantity, long orders) {
        return new DishDayTotals() {
            @Override
            public String getDay() {
                return day;
            }

            @Override
            public BigDecimal getRevenue() {
                return new BigDecimal(revenue);
            }

            @Override
            public long getQuantity() {
                return quantity;
            }

            @Override
            public long getOrderCount() {
                return orders;
            }
        };
    }
}
