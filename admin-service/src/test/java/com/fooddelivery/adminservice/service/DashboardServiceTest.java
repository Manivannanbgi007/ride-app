package com.fooddelivery.adminservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PaymentSummary;
import com.fooddelivery.adminservice.client.RemoteRecords.PaymentTotals;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantStats;
import com.fooddelivery.adminservice.client.RemoteRecords.UserStats;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.dto.response.DashboardResponse;
import com.fooddelivery.adminservice.dto.response.OrderActivityResponse;
import com.fooddelivery.adminservice.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

/** How the dashboard is put together from the services' answers, including missing ones. */
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-07");

    private final DashboardService dashboardService = new DashboardService(null, null,
            new AppProperties("Asia/Kolkata", Duration.ofSeconds(5), null),
            Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void figuresComeFromTheServicesThatAnswered() {
        UserStats users = new UserStats(TODAY, TODAY, Map.of(), 25_430, 25_000, 430, 120, Map.of());
        RestaurantStats restaurants = new RestaurantStats(TODAY, TODAY, Map.of(), 850, 720, 90, 12, 28, 3, Map.of());
        OrderStats orders = new OrderStats(TODAY.minusDays(1), TODAY, "INR", 6_000, 5_000, 300, 700,
                new BigDecimal("1500000.00"), new BigDecimal("20000.00"), new BigDecimal("300.00"),
                Map.of("DELIVERED", 5_000L), Map.of(),
                List.of(new OrderStats.Day(TODAY.minusDays(1), 2_760, 2_500, 150, new BigDecimal("700000.00")),
                        new OrderStats.Day(TODAY, 3_240, 2_500, 150, new BigDecimal("845000.00"))),
                new OrderStats.Overall(90_000, 80_000, 125, new BigDecimal("24000000.00")));
        PaymentSummary paymentsToday = new PaymentSummary(TODAY, TODAY, Map.of(
                "SUCCESS", new PaymentTotals(1_900, new BigDecimal("570000.00")),
                "REFUNDED", new PaymentTotals(40, new BigDecimal("12000.00")),
                "FAILED", new PaymentTotals(60, new BigDecimal("18000.00"))), Map.of(), null, null, "INR");

        DashboardService.Answers answers = new DashboardService.Answers(users, restaurants, null, orders, null,
                paymentsToday, null, null, null);
        DashboardResponse dashboard = dashboardService.assemble(TODAY, TODAY.minusDays(1), answers,
                List.of("catalog-service", "delivery-service"));

        DashboardResponse.Summary summary = dashboard.summary();
        assertThat(summary.totalCustomers()).isEqualTo(25_430);
        assertThat(summary.inactiveRestaurants()).isEqualTo(102); // deactivated + suspended
        assertThat(summary.todaysOrders()).isEqualTo(3_240);       // the charts' last day is today
        assertThat(summary.todaysRevenue()).isEqualByComparingTo("845000.00");
        assertThat(summary.pendingOrders()).isEqualTo(125);       // not finished now, whenever placed
        assertThat(summary.totalRevenue()).isEqualByComparingTo("24000000.00");
        assertThat(summary.todaysSuccessfulPayments()).isEqualTo(1_940); // refunded ones went through first
        assertThat(summary.todaysFailedPayments()).isEqualTo(60);
        // catalog-service and delivery-service did not answer: their figures are unknown, not zero
        assertThat(summary.totalMenuItems()).isNull();
        assertThat(summary.activeDeliveryPartners()).isNull();
        assertThat(dashboard.charts().deliveryPerformance()).isNull();
        assertThat(dashboard.charts().ordersPerDay()).hasSize(2);
        assertThat(dashboard.unavailable()).containsExactly("catalog-service", "delivery-service");
    }

    @Test
    void withoutOrderServiceTheOrderFiguresAreUnknown() {
        DashboardService.Answers nothing = new DashboardService.Answers(null, null, null, null, null, null, null, null,
                null);
        DashboardResponse dashboard = dashboardService.assemble(TODAY, TODAY, nothing, List.of("order-service"));

        assertThat(dashboard.summary().todaysOrders()).isNull();
        assertThat(dashboard.summary().totalRevenue()).isNull();
        assertThat(dashboard.charts().ordersPerDay()).isEmpty();
        assertThat(dashboard.currency()).isNull();
    }

    @Test
    void aDayWeekOrMonthInThePastIsAskedAsChosen() {
        // Monday to Sunday of a finished week, and a whole finished month
        ReportPeriod week = DashboardService.activityPeriod(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"), TODAY);
        assertThat(week).isEqualTo(new ReportPeriod(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04")));
        ReportPeriod month = DashboardService.activityPeriod(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"), TODAY);
        assertThat(month.toDate()).isEqualTo("2026-02-28");
        ReportPeriod oneDay = DashboardService.activityPeriod(TODAY, TODAY, TODAY);
        assertThat(oneDay).isEqualTo(new ReportPeriod(TODAY, TODAY));
    }

    @Test
    void daysStillToComeAreCutAtToday() {
        // The running week and month end after today
        ReportPeriod week = DashboardService.activityPeriod(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11"), TODAY);
        assertThat(week.toDate()).isEqualTo(TODAY);
        ReportPeriod month = DashboardService.activityPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), TODAY);
        assertThat(month).isEqualTo(new ReportPeriod(LocalDate.parse("2026-10-01"), TODAY));
    }

    @Test
    void invalidPeriodsAreRefused() {
        assertThatThrownBy(() -> DashboardService.activityPeriod(TODAY.plusDays(1), TODAY.plusDays(1), TODAY))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> DashboardService.activityPeriod(TODAY, TODAY.minusDays(1), TODAY))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> DashboardService.activityPeriod(TODAY.minusDays(400), TODAY, TODAY))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("366");
    }

    @Test
    void orderActivityHasThePeriodsDaysAndPaymentOutcome() {
        ReportPeriod period = new ReportPeriod(TODAY.minusDays(1), TODAY);
        OrderStats orders = new OrderStats(period.fromDate(), period.toDate(), "INR", 10, 6, 2, 2,
                new BigDecimal("1500.00"), new BigDecimal("0.00"), new BigDecimal("150.00"), Map.of(), Map.of(),
                List.of(new OrderStats.Day(period.fromDate(), 4, 3, 1, new BigDecimal("900.00")),
                        new OrderStats.Day(TODAY, 6, 3, 1, new BigDecimal("600.00"))),
                new OrderStats.Overall(0, 0, 0, BigDecimal.ZERO));
        PaymentSummary payments = new PaymentSummary(period.fromDate(), period.toDate(), Map.of(
                "SUCCESS", new PaymentTotals(8, new BigDecimal("1200.00")),
                "REFUNDED", new PaymentTotals(1, new BigDecimal("100.00")),
                "FAILED", new PaymentTotals(2, new BigDecimal("200.00"))), Map.of(), new BigDecimal("1100.00"),
                new BigDecimal("150.00"), "INR");

        OrderActivityResponse activity = dashboardService.assembleOrderActivity(period, orders, payments, List.of());

        assertThat(activity.fromDate()).isEqualTo(period.fromDate());
        assertThat(activity.toDate()).isEqualTo(TODAY);
        assertThat(activity.currency()).isEqualTo("INR");
        assertThat(activity.ordersPerDay()).hasSize(2);
        // The refunded money includes partial refunds (a payment that stays SUCCESS), so it is not 1 x 100
        assertThat(activity.paymentOutcome())
                .isEqualTo(new DashboardResponse.PaymentOutcome(9, 2, 1, new BigDecimal("150.00")));
    }

    @Test
    void aServiceThatDoesNotAnswerLeavesItsFiguresUnknownNotZero() {
        ReportPeriod period = new ReportPeriod(TODAY, TODAY);
        OrderActivityResponse activity = dashboardService.assembleOrderActivity(period, null, null,
                List.of("order-service", "payment-service"));

        assertThat(activity.ordersPerDay()).isEmpty();
        assertThat(activity.currency()).isNull();
        assertThat(activity.paymentOutcome()).isNull();
        assertThat(activity.unavailable()).containsExactly("order-service", "payment-service");
    }
}
