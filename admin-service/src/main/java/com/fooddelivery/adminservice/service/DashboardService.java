package com.fooddelivery.adminservice.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fooddelivery.adminservice.client.PlatformService;
import com.fooddelivery.adminservice.client.RemoteRecords.DeliveryReport;
import com.fooddelivery.adminservice.client.RemoteRecords.MenuItemStats;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PartnerStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PaymentSummary;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantPerformance;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantStats;
import com.fooddelivery.adminservice.client.RemoteRecords.UserStats;
import com.fooddelivery.adminservice.client.StatsClient;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.dto.response.DashboardResponse;
import com.fooddelivery.adminservice.dto.response.OrderActivityResponse;
import com.fooddelivery.adminservice.exception.InvalidRequestException;
import com.fooddelivery.adminservice.service.ParallelCalls.Batch;
import com.fooddelivery.adminservice.service.ParallelCalls.Call;
import com.fooddelivery.adminservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The admin dashboard, put together from the numbers each service keeps about its own data (admin functions never
 * read another service's database). All services are asked at once; a missing answer leaves its figures empty.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int TOP = 5;
    static final String SUCCESS = "SUCCESS";
    static final String REFUNDED = "REFUNDED";
    static final String FAILED = "FAILED";

    private final StatsClient statsClient;
    private final ParallelCalls parallelCalls;
    private final AppProperties appProperties;
    private final Clock clock;

    /** @param days how many days the charts cover, ending today */
    public DashboardResponse getDashboard(int days, CallerInfo caller) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(appProperties.businessTimeZone())));
        LocalDate chartsFrom = today.minusDays(days - 1L);

        Batch batch = parallelCalls.batch();
        Call<UserStats> users = batch.start(PlatformService.USER, () -> statsClient.userStats(today, today, caller));
        Call<RestaurantStats> restaurants = batch.start(PlatformService.RESTAURANT,
                () -> statsClient.restaurantStats(today, today, caller));
        Call<MenuItemStats> menuItems = batch.start(PlatformService.CATALOG, () -> statsClient.menuItemStats(caller));
        // The charts' last day is today: today's order figures come from the same answer
        Call<OrderStats> orders = batch.start(PlatformService.ORDER, () -> statsClient.orderStats(chartsFrom, today, caller));
        Call<RestaurantPerformance> performance = batch.start(PlatformService.ORDER,
                () -> statsClient.restaurantPerformance(chartsFrom, today, TOP, caller));
        Call<PaymentSummary> paymentsToday = batch.start(PlatformService.PAYMENT,
                () -> statsClient.paymentSummary(today, today, caller));
        Call<PaymentSummary> paymentsInPeriod = batch.start(PlatformService.PAYMENT,
                () -> statsClient.paymentSummary(chartsFrom, today, caller));
        Call<DeliveryReport> deliveries = batch.start(PlatformService.DELIVERY,
                () -> statsClient.deliveryReport(chartsFrom, today, caller));
        Call<PartnerStats> partners = batch.start(PlatformService.DELIVERY,
                () -> statsClient.partnerStats(chartsFrom, today, TOP, caller));

        Answers answers = new Answers(batch.await(users), batch.await(restaurants), batch.await(menuItems),
                batch.await(orders), batch.await(performance), batch.await(paymentsToday),
                batch.await(paymentsInPeriod), batch.await(deliveries), batch.await(partners));
        return assemble(today, chartsFrom, answers, batch.unavailable());
    }

    /**
     * The order charts of one chosen period (a day, a week, a month...), for "Chart Order" and "Order Rates". Only
     * the two services that own those numbers are asked, at the same time, and only for these days.
     *
     * @param fromDate first day (business time zone); not after today
     * @param toDate   last day, included; a day still to come is cut at today
     */
    public OrderActivityResponse getOrderActivity(LocalDate fromDate, LocalDate toDate, CallerInfo caller) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(appProperties.businessTimeZone())));
        ReportPeriod period = activityPeriod(fromDate, toDate, today);

        Batch batch = parallelCalls.batch();
        Call<OrderStats> orders = batch.start(PlatformService.ORDER,
                () -> statsClient.orderStats(period.fromDate(), period.toDate(), caller));
        Call<PaymentSummary> payments = batch.start(PlatformService.PAYMENT,
                () -> statsClient.paymentSummary(period.fromDate(), period.toDate(), caller));
        return assembleOrderActivity(period, batch.await(orders), batch.await(payments), batch.unavailable());
    }

    /** The days to ask for: as chosen, but not into the future, and no more than a report may cover. */
    static ReportPeriod activityPeriod(LocalDate fromDate, LocalDate toDate, LocalDate today) {
        if (fromDate.isAfter(today)) {
            throw new InvalidRequestException("fromDate must not be in the future");
        }
        return ReportPeriod.of(fromDate, toDate.isAfter(today) ? today : toDate, today);
    }

    OrderActivityResponse assembleOrderActivity(ReportPeriod period, OrderStats orders, PaymentSummary payments,
                                                List<String> unavailable) {
        List<DashboardResponse.DayPoint> ordersPerDay = orders == null ? List.of() : orders.perDay().stream()
                .map(day -> new DashboardResponse.DayPoint(day.date(), day.orders(), day.completed(),
                        day.cancelled(), day.revenue()))
                .toList();
        return new OrderActivityResponse(period.fromDate(), period.toDate(), ifAnswered(orders, OrderStats::currency),
                ordersPerDay, ifAnswered(payments, DashboardService::paymentOutcome), unavailable);
    }

    private static DashboardResponse.PaymentOutcome paymentOutcome(PaymentSummary summary) {
        return new DashboardResponse.PaymentOutcome(summary.count(SUCCESS) + summary.count(REFUNDED),
                summary.count(FAILED), summary.count(REFUNDED), summary.refundedAmount());
    }

    /** What each service answered; null where a service did not answer (its figures are then unknown). */
    record Answers(UserStats users, RestaurantStats restaurants, MenuItemStats menuItems, OrderStats orders,
                   RestaurantPerformance performance, PaymentSummary paymentsToday, PaymentSummary paymentsInPeriod,
                   DeliveryReport deliveries, PartnerStats partners) {
    }

    /** Builds the response from whatever answered. */
    DashboardResponse assemble(LocalDate today, LocalDate chartsFrom, Answers answers, List<String> unavailable) {
        return new DashboardResponse(today, chartsFrom, ifAnswered(answers.orders(), OrderStats::currency),
                clock.instant(), summary(answers), charts(answers), unavailable);
    }

    private static DashboardResponse.Summary summary(Answers answers) {
        RestaurantStats restaurants = answers.restaurants();
        MenuItemStats menuItems = answers.menuItems();
        OrderStats orders = answers.orders();
        PaymentSummary paymentsToday = answers.paymentsToday();
        PartnerStats partners = answers.partners();
        // The charts' last day is today
        OrderStats.Day today = ifAnswered(orders, stats -> stats.perDay().isEmpty() ? null : stats.perDay().getLast());
        return new DashboardResponse.Summary(
                ifAnswered(answers.users(), UserStats::customers),
                ifAnswered(restaurants, RestaurantStats::total),
                ifAnswered(restaurants, RestaurantStats::active),
                ifAnswered(restaurants, stats -> stats.inactive() + stats.suspended()),
                ifAnswered(restaurants, RestaurantStats::pendingApproval),
                ifAnswered(menuItems, MenuItemStats::total),
                ifAnswered(menuItems, MenuItemStats::active),
                ifAnswered(today, OrderStats.Day::orders),
                ifAnswered(today, OrderStats.Day::completed),
                ifAnswered(today, OrderStats.Day::cancelled),
                ifAnswered(orders, stats -> stats.overall().inProgress()),
                ifAnswered(today, OrderStats.Day::revenue),
                ifAnswered(orders, stats -> stats.overall().revenue()),
                ifAnswered(paymentsToday, summary -> summary.count(SUCCESS) + summary.count(REFUNDED)),
                ifAnswered(paymentsToday, summary -> summary.count(FAILED)),
                ifAnswered(partners, PartnerStats::online),
                ifAnswered(partners, PartnerStats::waitingForPartner));
    }

    private static DashboardResponse.Charts charts(Answers answers) {
        OrderStats orders = answers.orders();
        List<DashboardResponse.DayPoint> ordersPerDay = orders == null ? List.of() : orders.perDay().stream()
                .map(day -> new DashboardResponse.DayPoint(day.date(), day.orders(), day.completed(),
                        day.cancelled(), day.revenue()))
                .toList();
        List<DashboardResponse.RestaurantPoint> restaurants = answers.performance() == null ? List.of()
                : answers.performance().topByRevenue().stream()
                        .map(row -> new DashboardResponse.RestaurantPoint(row.restaurantId(), row.restaurantName(),
                                row.orders(), row.completed(), row.revenue(), row.cancellationRate()))
                        .toList();
        List<DashboardResponse.PartnerPoint> partners = answers.partners() == null ? List.of()
                : answers.partners().topPartners().stream()
                        .map(row -> new DashboardResponse.PartnerPoint(row.userId(), row.name(), row.delivered(),
                                row.averageDeliveryMinutes()))
                        .toList();
        return new DashboardResponse.Charts(
                ordersPerDay,
                orders == null ? Map.of() : orders.byStatus(),
                ifAnswered(answers.paymentsInPeriod(), DashboardService::paymentOutcome),
                restaurants,
                ifAnswered(answers.deliveries(), report -> new DashboardResponse.DeliveryPerformance(
                        report.delivered(), report.cancelled(), report.averageDeliveryMinutes(), report.delayedNow(),
                        partners)));
    }

    /** A figure from a service's answer, or null (unknown, not zero) when the service did not answer. */
    private static <T, R> R ifAnswered(T answer, Function<T, R> figure) {
        return answer == null ? null : figure.apply(answer);
    }
}
