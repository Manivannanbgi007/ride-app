package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.response.CalendarStatsResponse;
import com.fooddelivery.orderservice.dto.response.CustomerOrderStatsResponse;
import com.fooddelivery.orderservice.dto.response.LocationStatsResponse;
import com.fooddelivery.orderservice.dto.response.OrderStatsResponse;
import com.fooddelivery.orderservice.dto.response.RestaurantPerformanceResponse;
import com.fooddelivery.orderservice.dto.response.SalesSummaryResponse;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.DayTotals;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.HourTotals;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.OrderTotals;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.RestaurantTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order numbers for the admin dashboard and reports (the query side of order-service). Everything is counted
 * and summed in the database; this class only folds the grouped rows into responses.
 */
@Service
@RequiredArgsConstructor
public class OrderStatsService {

    /** The calendar may show more than a year (a year plus the days around it). */
    static final int CALENDAR_MAX_DAYS = 400;
    static final List<String> FREQUENCY_BUCKETS = List.of("1", "2-3", "4-5", "6+");
    private static final int HOURS_PER_DAY = 24;

    private final OrderStatsRepository orderStatsRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OrderStatsResponse getStats(LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = businessZone();
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, zone, clock);
        Totals inPeriod = Totals.of(orderStatsRepository.sumPerStatusAndMethod(period.from(), period.before()));
        Totals overall = Totals.of(orderStatsRepository.sumPerStatusAndMethodAllTime());

        Map<PaymentMethod, OrderStatsResponse.MethodTotals> byMethod = new EnumMap<>(PaymentMethod.class);
        inPeriod.byMethod.forEach((method, totals) -> byMethod.put(method,
                new OrderStatsResponse.MethodTotals(totals.orders, totals.completed, money(totals.revenue))));
        BigDecimal averageOrderValue = inPeriod.completed == 0 ? money(BigDecimal.ZERO)
                : inPeriod.revenue.divide(BigDecimal.valueOf(inPeriod.completed), 2, RoundingMode.HALF_UP);

        return new OrderStatsResponse(period.fromDate(), period.toDate(), currency(),
                inPeriod.orders, inPeriod.completed, inPeriod.cancelled, inPeriod.inProgress,
                money(inPeriod.revenue), money(inPeriod.discounts), averageOrderValue,
                inPeriod.byStatus, byMethod, perDay(period, zone),
                new OrderStatsResponse.Overall(overall.orders, overall.completed, overall.inProgress, money(overall.revenue)));
    }

    /**
     * Order counts per business day for the admin calendar, only days with orders. Same definitions as the dashboard's
     * per-day figures (so the numbers agree): placed = every order created that day, delivered = DELIVERED orders and
     * cancelled = CANCELLED + REJECTED, all counted on the day the order was placed. There is no delivered-at column,
     * and order_timeline has no index on (status, created_at), so the delivery time is not used.
     */
    @Transactional(readOnly = true)
    public CalendarStatsResponse getCalendar(LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = businessZone();
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, zone, clock, CALENDAR_MAX_DAYS);
        List<CalendarStatsResponse.Day> days = perDay(period, zone).stream()
                .filter(day -> day.orders() > 0)
                .map(day -> new CalendarStatsResponse.Day(day.date(), day.orders(), day.completed(), day.cancelled()))
                .toList();
        return new CalendarStatsResponse(days);
    }

    @Transactional(readOnly = true)
    public RestaurantPerformanceResponse getRestaurantPerformance(LocalDate fromDate, LocalDate toDate, int limit) {
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, businessZone(), clock);
        Instant from = period.from();
        Instant before = period.before();
        return new RestaurantPerformanceResponse(period.fromDate(), period.toDate(), currency(),
                orderStatsRepository.countRestaurantsWithOrders(from, before),
                restaurants(orderStatsRepository.topRestaurantsByRevenue(from, before, Limit.of(limit))),
                restaurants(orderStatsRepository.lowestRestaurantsByRevenue(from, before, Limit.of(limit))),
                restaurants(orderStatsRepository.mostCancelledRestaurants(from, before, Limit.of(limit))));
    }

    @Transactional(readOnly = true)
    public CustomerOrderStatsResponse getCustomerStats(LocalDate fromDate, LocalDate toDate, int limit) {
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, businessZone(), clock);
        long customers = orderStatsRepository.countCustomersWithOrders(period.from(), period.before());
        long orders = Totals.of(orderStatsRepository.sumPerStatusAndMethod(period.from(), period.before())).orders;

        Map<String, Long> frequency = new LinkedHashMap<>();
        FREQUENCY_BUCKETS.forEach(bucket -> frequency.put(bucket, 0L));
        orderStatsRepository.countCustomersPerOrderFrequency(period.from(), period.before())
                .forEach(row -> frequency.put(row.getBucket(), row.getCustomers()));

        List<CustomerOrderStatsResponse.Customer> top = orderStatsRepository
                .topCustomersBySpend(period.from(), period.before(), Limit.of(limit)).stream()
                .map(row -> new CustomerOrderStatsResponse.Customer(row.getCustomerId(), row.getCustomerName(),
                        row.getOrderCount(), row.getDeliveredCount(), money(row.getSpent())))
                .toList();
        BigDecimal perCustomer = customers == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(orders).divide(BigDecimal.valueOf(customers), 2, RoundingMode.HALF_UP);
        return new CustomerOrderStatsResponse(period.fromDate(), period.toDate(), currency(), customers, orders,
                perCustomer, frequency, top);
    }

    @Transactional(readOnly = true)
    public LocationStatsResponse getLocationStats(LocalDate fromDate, LocalDate toDate, int limit) {
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, businessZone(), clock);
        List<LocationStatsResponse.City> cities = orderStatsRepository
                .sumPerCity(period.from(), period.before(), Limit.of(limit)).stream()
                .map(row -> new LocationStatsResponse.City(row.getCity(), row.getOrderCount(), row.getDeliveredCount(),
                        money(row.getRevenue())))
                .toList();
        return new LocationStatsResponse(period.fromDate(), period.toDate(), currency(), cities);
    }

    /**
     * One restaurant's orders and sales per business day (its owner's dashboard), with the platform's definitions:
     * orders = placed that day, completed = DELIVERED, cancelled = CANCELLED + REJECTED, revenue = delivered orders.
     */
    @Transactional(readOnly = true)
    public List<OrderStatsResponse.Day> perDayForRestaurant(Long restaurantId, LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = businessZone();
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, zone, clock);
        return perDay(period, zone, (start, end, offset) ->
                orderStatsRepository.sumPerDayAndStatusForRestaurant(restaurantId, start, end, offset));
    }

    /**
     * One customer's own orders and spending per business day of a period (their dashboard): orders = placed that
     * day, completed = DELIVERED, cancelled = CANCELLED + REJECTED, revenue = what the delivered orders cost them.
     */
    @Transactional(readOnly = true)
    public List<OrderStatsResponse.Day> perDayForCustomer(Long customerId, StatsPeriod period) {
        return perDay(period, businessZone(), (start, end, offset) ->
                orderStatsRepository.sumPerDayAndStatusForCustomer(customerId, start, end, offset));
    }

    /** Grouped day rows of a part of the period in which the UTC offset stays the same. */
    @FunctionalInterface
    interface DayRows {
        List<DayTotals> between(Instant start, Instant end, String utcOffset);
    }

    /** The whole platform's per-day figures (the admin dashboard and calendar). */
    List<OrderStatsResponse.Day> perDay(StatsPeriod period, ZoneId zone) {
        return perDay(period, zone, orderStatsRepository::sumPerDayAndStatus);
    }

    /**
     * Orders per business day, every day of the period. The database works out the date with one UTC offset per
     * query, so a period that crosses a daylight-saving change is queried in parts, one per offset.
     */
    List<OrderStatsResponse.Day> perDay(StatsPeriod period, ZoneId zone, DayRows dayRows) {
        Map<LocalDate, DayCounts> days = new TreeMap<>();
        for (LocalDate day = period.fromDate(); !day.isAfter(period.toDate()); day = day.plusDays(1)) {
            days.put(day, new DayCounts());
        }
        forEachOffset(period, zone, (start, end, offset) -> {
            for (DayTotals row : dayRows.between(start, end, offset)) {
                days.computeIfAbsent(LocalDate.parse(row.getDay()), day -> new DayCounts())
                        .add(row.getStatus(), row.getOrderCount(), row.getTotalAmount());
            }
        });
        List<OrderStatsResponse.Day> result = new ArrayList<>();
        days.forEach((day, counts) -> result.add(new OrderStatsResponse.Day(day, counts.orders, counts.completed,
                counts.cancelled, money(counts.revenue))));
        return result;
    }

    /**
     * One restaurant's orders and sales per hour of the day (0-23, business time zone), summed over every day of the
     * period: when its orders come in. The same definitions as the per-day figures; all 24 hours are listed.
     */
    @Transactional(readOnly = true)
    public List<SalesSummaryResponse.Hour> perHourForRestaurant(Long restaurantId, LocalDate fromDate,
                                                                 LocalDate toDate) {
        ZoneId zone = businessZone();
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, zone, clock);
        DayCounts[] hours = new DayCounts[HOURS_PER_DAY];
        Arrays.setAll(hours, hour -> new DayCounts());
        forEachOffset(period, zone, (start, end, offset) -> {
            for (HourTotals row : orderStatsRepository.sumPerHourAndStatusForRestaurant(restaurantId, start, end,
                    offset)) {
                hours[row.getHour()].add(row.getStatus(), row.getOrderCount(), row.getTotalAmount());
            }
        });
        List<SalesSummaryResponse.Hour> result = new ArrayList<>();
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            DayCounts counts = hours[hour];
            result.add(new SalesSummaryResponse.Hour(hour, counts.orders, counts.completed, counts.cancelled,
                    money(counts.revenue)));
        }
        return result;
    }

    /** A part of a period in which the business time zone keeps one UTC offset ("+05:30"). */
    @FunctionalInterface
    private interface OffsetPart {
        void query(Instant start, Instant end, String utcOffset);
    }

    /**
     * Calls `part` for each part of the period with one UTC offset: the whole period, or two parts around a
     * daylight-saving change (the database converts the times with one offset per query).
     */
    private static void forEachOffset(StatsPeriod period, ZoneId zone, OffsetPart part) {
        Instant start = period.from();
        while (start.isBefore(period.before())) {
            ZoneOffsetTransition change = zone.getRules().nextTransition(start);
            Instant end = change == null || !change.getInstant().isBefore(period.before())
                    ? period.before() : change.getInstant();
            part.query(start, end, sqlOffset(zone.getRules().getOffset(start)));
            start = end;
        }
    }

    /** MySQL's CONVERT_TZ wants "+05:30" (and "+00:00" for UTC, where Java says "Z"). */
    static String sqlOffset(ZoneOffset offset) {
        return offset.getTotalSeconds() == 0 ? "+00:00" : offset.getId();
    }

    private static List<RestaurantPerformanceResponse.Restaurant> restaurants(List<RestaurantTotals> rows) {
        return rows.stream()
                .map(row -> new RestaurantPerformanceResponse.Restaurant(row.getRestaurantId(), row.getRestaurantName(),
                        row.getOrderCount(), row.getDeliveredCount(), row.getCancelledCount(), money(row.getRevenue()),
                        percent(row.getCancelledCount(), row.getOrderCount())))
                .toList();
    }

    private static BigDecimal percent(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private ZoneId businessZone() {
        return ZoneId.of(appProperties.businessTimeZone());
    }

    private String currency() {
        return appProperties.pricing().currency();
    }

    /** The figures of one day (or one hour): orders placed, delivered, cancelled or rejected, delivered value. */
    private static final class DayCounts {
        private long orders;
        private long completed;
        private long cancelled;
        private BigDecimal revenue = BigDecimal.ZERO;

        private void add(String statusName, long orderCount, BigDecimal totalAmount) {
            OrderStatus status = OrderStatus.valueOf(statusName);
            orders += orderCount;
            if (status == OrderStatus.DELIVERED) {
                completed += orderCount;
                revenue = revenue.add(totalAmount);
            } else if (status.isCancelledOrRejected()) {
                cancelled += orderCount;
            }
        }
    }

    /** Folds rows per status and payment method into the figures the responses need. */
    private static final class Totals {
        private final Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        private final Map<PaymentMethod, Totals> byMethod = new EnumMap<>(PaymentMethod.class);
        private long orders;
        private long completed;
        private long cancelled;
        private long inProgress;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal discounts = BigDecimal.ZERO;

        static Totals of(List<OrderTotals> rows) {
            Totals totals = new Totals();
            for (OrderTotals row : rows) {
                totals.byStatus.merge(row.getStatus(), row.getOrderCount(), Long::sum);
                totals.add(row);
                totals.byMethod.computeIfAbsent(row.getPaymentMethod(), method -> new Totals()).add(row);
            }
            return totals;
        }

        private void add(OrderTotals row) {
            orders += row.getOrderCount();
            if (row.getStatus() == OrderStatus.DELIVERED) {
                completed += row.getOrderCount();
                revenue = revenue.add(row.getTotalAmount());
                discounts = discounts.add(row.getDiscountAmount());
            } else if (row.getStatus().isCancelledOrRejected()) {
                cancelled += row.getOrderCount();
            } else {
                inProgress += row.getOrderCount();
            }
        }
    }
}
