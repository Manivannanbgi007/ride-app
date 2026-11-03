package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.orderservice.dto.response.CalendarStatsResponse;
import com.fooddelivery.orderservice.dto.response.OrderStatsResponse;
import com.fooddelivery.orderservice.dto.response.SalesSummaryResponse;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.exception.InvalidRequestException;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.DayTotals;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.HourTotals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderStatsServiceTest {

    @Mock
    private OrderStatsRepository orderStatsRepository;

    @Test
    void everyDayOfThePeriodIsListedAlsoWithoutOrders() {
        OrderStatsService stats = new OrderStatsService(orderStatsRepository, TestProperties.appProperties(),
                Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        StatsPeriod period = StatsPeriod.of(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), kolkata,
                Clock.systemUTC());
        when(orderStatsRepository.sumPerDayAndStatus(period.from(), period.before(), "+05:30")).thenReturn(List.of(
                day("2026-10-05", "DELIVERED", 3, "900.00"), day("2026-10-05", "CANCELLED", 1, "120.00"),
                day("2026-10-07", "PLACED", 2, "400.00")));

        List<OrderStatsResponse.Day> days = stats.perDay(period, kolkata);

        assertThat(days).extracting(OrderStatsResponse.Day::date).containsExactly(
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-07"));
        assertThat(days.getFirst()).isEqualTo(new OrderStatsResponse.Day(LocalDate.parse("2026-10-05"), 4, 3, 1,
                new BigDecimal("900.00")));
        assertThat(days.get(1).orders()).isZero();
        assertThat(days.get(2).revenue()).isEqualByComparingTo("0.00"); // placed, not delivered yet
    }

    @Test
    void calendarListsOnlyDaysWithOrdersAndCountsEveryStatusGroup() {
        OrderStatsService stats = new OrderStatsService(orderStatsRepository, TestProperties.appProperties(),
                Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));
        // Kolkata: the range starts at local midnight, 2026-10-04T18:30:00Z, and ends before 2026-10-07T18:30:00Z
        when(orderStatsRepository.sumPerDayAndStatus(Instant.parse("2026-10-04T18:30:00Z"),
                Instant.parse("2026-10-07T18:30:00Z"), "+05:30")).thenReturn(List.of(
                day("2026-10-05", "DELIVERED", 3, "900.00"), day("2026-10-05", "REJECTED", 1, "120.00"),
                day("2026-10-05", "CANCELLED", 2, "120.00"), day("2026-10-05", "PAYMENT_PENDING", 1, "50.00"),
                day("2026-10-07", "PLACED", 2, "400.00")));

        CalendarStatsResponse calendar = stats.getCalendar(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"));

        assertThat(calendar.days()).containsExactly(
                new CalendarStatsResponse.Day(LocalDate.parse("2026-10-05"), 7, 3, 3),
                new CalendarStatsResponse.Day(LocalDate.parse("2026-10-07"), 2, 0, 0));
    }

    @Test
    void calendarAllowsFourHundredDaysButNotMore() {
        OrderStatsService stats = new OrderStatsService(orderStatsRepository, TestProperties.appProperties(),
                Clock.systemUTC());
        when(orderStatsRepository.sumPerDayAndStatus(any(), any(), anyString())).thenReturn(List.of());
        LocalDate from = LocalDate.parse("2026-01-01");

        assertThat(stats.getCalendar(from, from.plusDays(399)).days()).isEmpty();
        assertThatThrownBy(() -> stats.getCalendar(from, from.plusDays(400)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> stats.getCalendar(from, from.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aPeriodAcrossADaylightSavingChangeIsQueriedOncePerOffset() {
        OrderStatsService stats = new OrderStatsService(orderStatsRepository, TestProperties.appProperties(),
                Clock.systemUTC());
        ZoneId london = ZoneId.of("Europe/London"); // clocks go back on 2026-10-25 at 01:00 UTC
        StatsPeriod period = StatsPeriod.of(LocalDate.parse("2026-10-24"), LocalDate.parse("2026-10-26"), london,
                Clock.systemUTC());
        when(orderStatsRepository.sumPerDayAndStatus(any(), any(), anyString())).thenReturn(List.of());

        assertThat(stats.perDay(period, london)).hasSize(3);

        Instant change = Instant.parse("2026-10-25T01:00:00Z");
        verify(orderStatsRepository).sumPerDayAndStatus(period.from(), change, "+01:00");
        verify(orderStatsRepository).sumPerDayAndStatus(change, period.before(), "+00:00");
    }

    @Test
    void everyHourOfTheDayIsListedWithTheRestaurantsOrdersOfThatHour() {
        OrderStatsService stats = new OrderStatsService(orderStatsRepository, TestProperties.appProperties(),
                Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));
        // Kolkata: 7 October from 00:00 IST (6 October 18:30 UTC) up to, not including, 8 October 00:00 IST
        when(orderStatsRepository.sumPerHourAndStatusForRestaurant(10L, Instant.parse("2026-10-06T18:30:00Z"),
                Instant.parse("2026-10-07T18:30:00Z"), "+05:30")).thenReturn(List.of(
                hour(13, "DELIVERED", 3, "900.00"), hour(13, "CANCELLED", 1, "120.00"),
                hour(20, "PLACED", 2, "400.00")));

        List<SalesSummaryResponse.Hour> hours = stats.perHourForRestaurant(10L, LocalDate.parse("2026-10-07"),
                LocalDate.parse("2026-10-07"));

        assertThat(hours).hasSize(24).extracting(SalesSummaryResponse.Hour::hour).startsWith(0, 1).endsWith(23);
        assertThat(hours.get(13)).isEqualTo(new SalesSummaryResponse.Hour(13, 4, 3, 1, new BigDecimal("900.00")));
        assertThat(hours.get(20)).isEqualTo(new SalesSummaryResponse.Hour(20, 2, 0, 0, new BigDecimal("0.00")));
        assertThat(hours.getFirst()).isEqualTo(new SalesSummaryResponse.Hour(0, 0, 0, 0, new BigDecimal("0.00")));
    }

    @Test
    void hoursAcrossADaylightSavingChangeAreQueriedOncePerOffsetAndAddedUp() {
        OrderStatsService stats = new OrderStatsService(orderStatsRepository,
                TestProperties.appProperties("Europe/London"), Clock.systemUTC());
        Instant start = Instant.parse("2026-10-23T23:00:00Z"); // 24 October 00:00 in London (BST)
        Instant change = Instant.parse("2026-10-25T01:00:00Z"); // clocks go back
        Instant end = Instant.parse("2026-10-27T00:00:00Z");   // 27 October 00:00 in London (GMT)
        when(orderStatsRepository.sumPerHourAndStatusForRestaurant(10L, start, change, "+01:00"))
                .thenReturn(List.of(hour(19, "DELIVERED", 1, "300.00")));
        when(orderStatsRepository.sumPerHourAndStatusForRestaurant(10L, change, end, "+00:00"))
                .thenReturn(List.of(hour(19, "DELIVERED", 2, "500.00")));

        List<SalesSummaryResponse.Hour> hours = stats.perHourForRestaurant(10L, LocalDate.parse("2026-10-24"),
                LocalDate.parse("2026-10-26"));

        assertThat(hours.get(19)).isEqualTo(new SalesSummaryResponse.Hour(19, 3, 3, 0, new BigDecimal("800.00")));
    }

    @Test
    void utcIsWrittenTheWayMySqlWantsIt() {
        assertThat(OrderStatsService.sqlOffset(ZoneOffset.UTC)).isEqualTo("+00:00");
        assertThat(OrderStatsService.sqlOffset(ZoneOffset.ofHoursMinutes(5, 30))).isEqualTo("+05:30");
        assertThat(OrderStatus.REJECTED.isCancelledOrRejected()).isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.isInProgress()).isTrue();
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
