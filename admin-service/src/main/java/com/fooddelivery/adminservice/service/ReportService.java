package com.fooddelivery.adminservice.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.PlatformService;
import com.fooddelivery.adminservice.client.RemoteRecords.CustomerOrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.DeliveryReport;
import com.fooddelivery.adminservice.client.RemoteRecords.LocationStats;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PartnerStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PaymentSummary;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantPerformance;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantStats;
import com.fooddelivery.adminservice.client.RemoteRecords.UserStats;
import com.fooddelivery.adminservice.client.StatsClient;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.dto.request.RevenueGroupBy;
import com.fooddelivery.adminservice.dto.response.CustomerReportResponse;
import com.fooddelivery.adminservice.dto.response.DashboardResponse;
import com.fooddelivery.adminservice.dto.response.DeliveryReportResponse;
import com.fooddelivery.adminservice.dto.response.OrderReportResponse;
import com.fooddelivery.adminservice.dto.response.RestaurantReportResponse;
import com.fooddelivery.adminservice.dto.response.RevenueReportResponse;
import com.fooddelivery.adminservice.service.ParallelCalls.Batch;
import com.fooddelivery.adminservice.service.ParallelCalls.Call;
import com.fooddelivery.adminservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Platform reports, each put together from the services that own the numbers (asked at the same time). If a
 * service does not answer, its part of the report is empty and it is named in "unavailable".
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final StatsClient statsClient;
    private final ParallelCalls parallelCalls;
    private final AppProperties appProperties;
    private final Clock clock;

    public OrderReportResponse orders(LocalDate fromDate, LocalDate toDate, int limit, CallerInfo caller) {
        ReportPeriod period = period(fromDate, toDate);
        Batch batch = parallelCalls.batch();
        Call<OrderStats> statsCall = batch.start(PlatformService.ORDER,
                () -> statsClient.orderStats(period.fromDate(), period.toDate(), caller));
        Call<RestaurantPerformance> restaurantsCall = batch.start(PlatformService.ORDER,
                () -> statsClient.restaurantPerformance(period.fromDate(), period.toDate(), limit, caller));
        Call<LocationStats> citiesCall = batch.start(PlatformService.ORDER,
                () -> statsClient.locationStats(period.fromDate(), period.toDate(), limit, caller));
        OrderStats stats = batch.await(statsCall);
        RestaurantPerformance restaurants = batch.await(restaurantsCall);
        LocationStats cities = batch.await(citiesCall);

        return new OrderReportResponse(period.fromDate(), period.toDate(), stats == null ? null : stats.currency(),
                stats == null ? null : stats.orders(),
                stats == null ? null : stats.completed(),
                stats == null ? null : stats.cancelled(),
                stats == null ? null : stats.inProgress(),
                stats == null ? null : stats.averageOrderValue(),
                stats == null ? Map.of() : stats.byStatus(),
                stats == null ? List.of() : days(stats),
                restaurants == null ? List.of() : restaurants.topByRevenue(),
                cities == null ? List.of() : cities.cities(),
                batch.unavailable());
    }

    public RevenueReportResponse revenue(LocalDate fromDate, LocalDate toDate, RevenueGroupBy groupBy, int limit,
                                         CallerInfo caller) {
        ReportPeriod period = period(fromDate, toDate);
        Batch batch = parallelCalls.batch();
        Call<OrderStats> statsCall = batch.start(PlatformService.ORDER,
                () -> statsClient.orderStats(period.fromDate(), period.toDate(), caller));
        Call<RestaurantPerformance> restaurantsCall = batch.start(PlatformService.ORDER,
                () -> statsClient.restaurantPerformance(period.fromDate(), period.toDate(), limit, caller));
        Call<PaymentSummary> paymentsCall = batch.start(PlatformService.PAYMENT,
                () -> statsClient.paymentSummary(period.fromDate(), period.toDate(), caller));
        OrderStats stats = batch.await(statsCall);
        RestaurantPerformance restaurants = batch.await(restaurantsCall);
        PaymentSummary payments = batch.await(paymentsCall);

        Map<String, RevenueReportResponse.MethodRevenue> byMethod = new LinkedHashMap<>();
        if (stats != null) {
            stats.byPaymentMethod().forEach((method, totals) -> byMethod.put(method,
                    new RevenueReportResponse.MethodRevenue(totals.orders(), totals.completed(), totals.revenue())));
        }
        return new RevenueReportResponse(period.fromDate(), period.toDate(), stats == null ? null : stats.currency(),
                groupBy,
                stats == null ? null : stats.revenue(),
                stats == null ? null : stats.discounts(),
                stats == null ? null : stats.averageOrderValue(),
                stats == null ? List.of() : RevenueGrouping.group(stats.perDay(), groupBy),
                restaurants == null ? List.of() : restaurants.topByRevenue(),
                byMethod,
                payments == null ? null
                        : new RevenueReportResponse.OnlinePayments(payments.collectedAmount(), payments.refundedAmount()),
                batch.unavailable());
    }

    public RestaurantReportResponse restaurants(LocalDate fromDate, LocalDate toDate, int limit, CallerInfo caller) {
        ReportPeriod period = period(fromDate, toDate);
        Batch batch = parallelCalls.batch();
        Call<RestaurantStats> statsCall = batch.start(PlatformService.RESTAURANT,
                () -> statsClient.restaurantStats(period.fromDate(), period.toDate(), caller));
        Call<RestaurantPerformance> performanceCall = batch.start(PlatformService.ORDER,
                () -> statsClient.restaurantPerformance(period.fromDate(), period.toDate(), limit, caller));
        RestaurantStats stats = batch.await(statsCall);
        RestaurantPerformance performance = batch.await(performanceCall);

        return new RestaurantReportResponse(period.fromDate(), period.toDate(),
                performance == null ? null : performance.currency(),
                stats == null ? null : stats.total(),
                stats == null ? null : stats.active(),
                stats == null ? null : stats.inactive(),
                stats == null ? null : stats.suspended(),
                stats == null ? null : stats.pendingApproval(),
                stats == null ? null : stats.newRestaurants(),
                stats == null ? Map.of() : stats.restaurantsNow(),
                performance == null ? null : performance.restaurantsWithOrders(),
                performance == null ? List.of() : performance.topByRevenue(),
                performance == null ? List.of() : performance.lowestByRevenue(),
                performance == null ? List.of() : performance.mostCancellations(),
                batch.unavailable());
    }

    public CustomerReportResponse customers(LocalDate fromDate, LocalDate toDate, int limit, CallerInfo caller) {
        ReportPeriod period = period(fromDate, toDate);
        Batch batch = parallelCalls.batch();
        Call<UserStats> usersCall = batch.start(PlatformService.USER,
                () -> statsClient.userStats(period.fromDate(), period.toDate(), caller));
        Call<CustomerOrderStats> ordersCall = batch.start(PlatformService.ORDER,
                () -> statsClient.customerOrderStats(period.fromDate(), period.toDate(), limit, caller));
        UserStats users = batch.await(usersCall);
        CustomerOrderStats orders = batch.await(ordersCall);

        Long notOrdering = users == null || orders == null ? null
                : Math.max(0, users.customers() - orders.customersWithOrders());
        return new CustomerReportResponse(period.fromDate(), period.toDate(), orders == null ? null : orders.currency(),
                users == null ? null : users.customers(),
                users == null ? null : users.activeCustomers(),
                users == null ? null : users.inactiveCustomers(),
                users == null ? null : users.newCustomers(),
                orders == null ? null : orders.customersWithOrders(),
                notOrdering,
                orders == null ? null : orders.ordersPerCustomer(),
                orders == null ? Map.of() : orders.orderFrequency(),
                orders == null ? List.of() : orders.topBySpend(),
                batch.unavailable());
    }

    public DeliveryReportResponse deliveries(LocalDate fromDate, LocalDate toDate, int limit, CallerInfo caller) {
        ReportPeriod period = period(fromDate, toDate);
        Batch batch = parallelCalls.batch();
        Call<DeliveryReport> reportCall = batch.start(PlatformService.DELIVERY,
                () -> statsClient.deliveryReport(period.fromDate(), period.toDate(), caller));
        Call<PartnerStats> partnersCall = batch.start(PlatformService.DELIVERY,
                () -> statsClient.partnerStats(period.fromDate(), period.toDate(), limit, caller));
        DeliveryReport report = batch.await(reportCall);
        PartnerStats partners = batch.await(partnersCall);

        return new DeliveryReportResponse(period.fromDate(), period.toDate(),
                report == null ? null : report.total(),
                report == null ? null : report.delivered(),
                report == null ? null : report.cancelled(),
                report == null ? null : report.averageDeliveryMinutes(),
                report == null ? null : report.delayedNow(),
                report == null ? Map.of() : report.byStatus(),
                partners == null ? null : partners.online(),
                partners == null ? null : partners.waitingForPartner(),
                partners == null ? List.of() : partners.topPartners(),
                batch.unavailable());
    }

    private ReportPeriod period(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(appProperties.businessTimeZone())));
        return ReportPeriod.of(fromDate, toDate, today);
    }

    private static List<DashboardResponse.DayPoint> days(OrderStats stats) {
        return stats.perDay().stream()
                .map(day -> new DashboardResponse.DayPoint(day.date(), day.orders(), day.completed(), day.cancelled(),
                        day.revenue()))
                .toList();
    }
}
