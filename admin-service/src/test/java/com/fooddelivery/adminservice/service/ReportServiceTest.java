package com.fooddelivery.adminservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fooddelivery.adminservice.client.RemoteRecords.CityRow;
import com.fooddelivery.adminservice.client.RemoteRecords.CustomerOrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.CustomerRow;
import com.fooddelivery.adminservice.client.RemoteRecords.DeliveryReport;
import com.fooddelivery.adminservice.client.RemoteRecords.LocationStats;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PartnerRow;
import com.fooddelivery.adminservice.client.RemoteRecords.PartnerStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PaymentSummary;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantPerformance;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantRow;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantStats;
import com.fooddelivery.adminservice.client.RemoteRecords.UserStats;
import com.fooddelivery.adminservice.client.StatsClient;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.dto.request.RevenueGroupBy;
import com.fooddelivery.adminservice.dto.response.CustomerReportResponse;
import com.fooddelivery.adminservice.dto.response.DeliveryReportResponse;
import com.fooddelivery.adminservice.dto.response.OrderReportResponse;
import com.fooddelivery.adminservice.dto.response.RestaurantReportResponse;
import com.fooddelivery.adminservice.dto.response.RevenueReportResponse;
import com.fooddelivery.adminservice.entity.Role;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.adminservice.web.CallerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Reports put together from the services' answers; a service that does not answer leaves its part unknown. */
class ReportServiceTest {

    private static final LocalDate FROM = LocalDate.parse("2026-09-28");
    private static final LocalDate TO = LocalDate.parse("2026-10-07");
    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "127.0.0.1", "req-1");
    private static final RemoteServiceException DOWN = new RemoteServiceException("test", new IllegalStateException("down"));

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AppProperties appProperties = new AppProperties("Asia/Kolkata", Duration.ofSeconds(5), null);
    private final StatsClient statsClient = mock(StatsClient.class);
    private final ReportService reportService = new ReportService(statsClient, new ParallelCalls(executor, appProperties),
            appProperties, Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void orderReportCombinesTotalsRankingsAndCities() {
        when(statsClient.orderStats(FROM, TO, ADMIN)).thenReturn(orderStats());
        when(statsClient.restaurantPerformance(FROM, TO, 10, ADMIN)).thenReturn(performance());
        when(statsClient.locationStats(FROM, TO, 10, ADMIN))
                .thenReturn(new LocationStats(FROM, TO, "INR", List.of(new CityRow("Bengaluru", 12, 9, money("3540.50")))));

        OrderReportResponse report = reportService.orders(FROM, TO, 10, ADMIN);

        assertThat(report.totalOrders()).isEqualTo(12);
        assertThat(report.averageOrderValue()).isEqualByComparingTo("393.39");
        assertThat(report.perDay()).hasSize(2);
        assertThat(report.byRestaurant()).extracting(RestaurantRow::restaurantName).containsExactly("Spice Hub");
        assertThat(report.byCity()).extracting(CityRow::city).containsExactly("Bengaluru");
        assertThat(report.unavailable()).isEmpty();
    }

    @Test
    void withoutOrderServiceTheOrderReportIsEmptyAndSaysWhy() {
        when(statsClient.orderStats(any(), any(), any())).thenThrow(DOWN);
        when(statsClient.restaurantPerformance(any(), any(), anyInt(), any())).thenThrow(DOWN);
        when(statsClient.locationStats(any(), any(), anyInt(), any())).thenThrow(DOWN);

        OrderReportResponse report = reportService.orders(FROM, TO, 10, ADMIN);

        assertThat(report.totalOrders()).isNull();     // unknown, not zero
        assertThat(report.byStatus()).isEmpty();
        assertThat(report.byRestaurant()).isEmpty();
        assertThat(report.unavailable()).containsExactly("order-service");
    }

    @Test
    void revenueReportGroupsByWeekAndAddsOnlinePayments() {
        when(statsClient.orderStats(FROM, TO, ADMIN)).thenReturn(orderStats());
        when(statsClient.restaurantPerformance(FROM, TO, 5, ADMIN)).thenReturn(performance());
        when(statsClient.paymentSummary(FROM, TO, ADMIN)).thenReturn(new PaymentSummary(FROM, TO, Map.of(), Map.of(),
                money("2800.00"), money("150.00"), "INR"));

        RevenueReportResponse report = reportService.revenue(FROM, TO, RevenueGroupBy.WEEK, 5, ADMIN);

        assertThat(report.revenue()).isEqualByComparingTo("3540.50");
        assertThat(report.periods()).hasSize(2);             // two ISO weeks: 28 Sep and 5 Oct
        assertThat(report.byPaymentMethod().get("UPI").revenue()).isEqualByComparingTo("2800.00");
        assertThat(report.onlinePayments().refunded()).isEqualByComparingTo("150.00");
    }

    @Test
    void customersWhoDidNotOrderAreTheRestOfTheCustomers() {
        when(statsClient.userStats(FROM, TO, ADMIN)).thenReturn(new UserStats(FROM, TO, Map.of(), 100, 95, 5, 12, Map.of()));
        when(statsClient.customerOrderStats(FROM, TO, 10, ADMIN)).thenReturn(new CustomerOrderStats(FROM, TO, "INR", 40,
                90, money("2.25"), Map.of("1", 20L, "2-4", 15L, "5+", 5L),
                List.of(new CustomerRow(9L, "Asha", 12, 12, money("4800.00")))));

        CustomerReportResponse report = reportService.customers(FROM, TO, 10, ADMIN);

        assertThat(report.totalCustomers()).isEqualTo(100);
        assertThat(report.orderingCustomers()).isEqualTo(40);
        assertThat(report.notOrdering()).isEqualTo(60);
        assertThat(report.topCustomers()).extracting(CustomerRow::customerName).containsExactly("Asha");
    }

    @Test
    void withoutOrderServiceCustomersWithoutOrdersAreUnknown() {
        when(statsClient.userStats(FROM, TO, ADMIN)).thenReturn(new UserStats(FROM, TO, Map.of(), 100, 95, 5, 12, Map.of()));
        when(statsClient.customerOrderStats(any(), any(), anyInt(), any())).thenThrow(DOWN);

        CustomerReportResponse report = reportService.customers(FROM, TO, 10, ADMIN);

        assertThat(report.totalCustomers()).isEqualTo(100);
        assertThat(report.notOrdering()).isNull();
        assertThat(report.unavailable()).containsExactly("order-service");
    }

    @Test
    void restaurantReportCombinesStatusesAndPerformance() {
        when(statsClient.restaurantStats(FROM, TO, ADMIN)).thenReturn(new RestaurantStats(FROM, TO,
                Map.of("ACTIVE", 720L), 850, 720, 90, 12, 28, 3, Map.of()));
        when(statsClient.restaurantPerformance(FROM, TO, 10, ADMIN)).thenReturn(performance());

        RestaurantReportResponse report = reportService.restaurants(FROM, TO, 10, ADMIN);

        assertThat(report.total()).isEqualTo(850);
        assertThat(report.pendingApproval()).isEqualTo(28);
        assertThat(report.restaurantsWithOrders()).isEqualTo(1);
        assertThat(report.topRestaurants()).hasSize(1);
        assertThat(report.currency()).isEqualTo("INR");
    }

    @Test
    void deliveryReportWithoutPartnerFigures() {
        when(statsClient.deliveryReport(FROM, TO, ADMIN)).thenReturn(new DeliveryReport(FROM, TO,
                Map.of("DELIVERED", 9L), 11, 9, 2, 31.5, 1));
        when(statsClient.partnerStats(any(), any(), anyInt(), any())).thenThrow(DOWN);

        DeliveryReportResponse report = reportService.deliveries(FROM, TO, 10, ADMIN);

        assertThat(report.completed()).isEqualTo(9);
        assertThat(report.averageDeliveryMinutes()).isEqualTo(31.5);
        assertThat(report.partnersOnline()).isNull();
        assertThat(report.topPartners()).isEmpty();
        assertThat(report.unavailable()).containsExactly("delivery-service");
    }

    @Test
    void deliveryReportWithPartners() {
        when(statsClient.deliveryReport(FROM, TO, ADMIN)).thenReturn(new DeliveryReport(FROM, TO, Map.of(), 0, 0, 0,
                null, 0));
        when(statsClient.partnerStats(FROM, TO, 3, ADMIN)).thenReturn(new PartnerStats(FROM, TO, Map.of(), 4, 1,
                List.of(new PartnerRow(5L, "Ravi", 30, 24.0, 1))));

        DeliveryReportResponse report = reportService.deliveries(FROM, TO, 3, ADMIN);

        assertThat(report.partnersOnline()).isEqualTo(4);
        assertThat(report.topPartners()).extracting(PartnerRow::name).containsExactly("Ravi");
    }

    private static OrderStats orderStats() {
        return new OrderStats(FROM, TO, "INR", 12, 9, 2, 1, money("3540.50"), money("100.00"), money("393.39"),
                Map.of("DELIVERED", 9L), Map.of("UPI", new OrderStats.MethodTotals(8, 7, money("2800.00"))),
                List.of(new OrderStats.Day(FROM, 5, 4, 1, money("1540.50")),
                        new OrderStats.Day(TO, 7, 5, 1, money("2000.00"))),
                new OrderStats.Overall(900, 850, 1, money("300000.00")));
    }

    private static RestaurantPerformance performance() {
        RestaurantRow spiceHub = new RestaurantRow(3L, "Spice Hub", 4, 4, 0, money("1200.00"), BigDecimal.ZERO);
        return new RestaurantPerformance(FROM, TO, "INR", 1, List.of(spiceHub), List.of(spiceHub), List.of());
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
