package com.fooddelivery.adminservice.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Numbers received from the services' admin APIs. Status names are plain strings ("tolerant reader"):
 * a new status in a service must not break the dashboard. Fields this service does not use are ignored.
 */
public final class RemoteRecords {

    private RemoteRecords() {
    }

    /** user-service: GET /api/v1/admin/users/stats */
    public record UserStats(LocalDate fromDate, LocalDate toDate, Map<String, Map<String, Long>> usersNow,
                            long customers, long activeCustomers, long inactiveCustomers, long newCustomers,
                            Map<String, Long> newUsersByRole) {
    }

    /** restaurant-service: GET /api/v1/admin/restaurants/stats */
    public record RestaurantStats(LocalDate fromDate, LocalDate toDate, Map<String, Long> restaurantsNow, long total,
                                  long active, long inactive, long suspended, long pendingApproval,
                                  long newRestaurants, Map<String, Long> newRestaurantsByStatus) {
    }

    /** catalog-service: GET /api/v1/admin/menu-items/stats */
    public record MenuItemStats(long total, long active, long disabled, long orderable, long soldOut) {
    }

    /** order-service: GET /api/v1/admin/orders/stats */
    public record OrderStats(LocalDate fromDate, LocalDate toDate, String currency, long orders, long completed,
                             long cancelled, long inProgress, BigDecimal revenue, BigDecimal discounts,
                             BigDecimal averageOrderValue, Map<String, Long> byStatus,
                             Map<String, MethodTotals> byPaymentMethod, List<Day> perDay, Overall overall) {

        public record MethodTotals(long orders, long completed, BigDecimal revenue) {
        }

        public record Day(LocalDate date, long orders, long completed, long cancelled, BigDecimal revenue) {
        }

        public record Overall(long orders, long completed, long inProgress, BigDecimal revenue) {
        }
    }

    /** order-service: GET /api/v1/admin/orders/stats/restaurants */
    public record RestaurantPerformance(LocalDate fromDate, LocalDate toDate, String currency, long restaurantsWithOrders,
                                        List<RestaurantRow> topByRevenue, List<RestaurantRow> lowestByRevenue,
                                        List<RestaurantRow> mostCancellations) {
    }

    public record RestaurantRow(Long restaurantId, String restaurantName, long orders, long completed, long cancelled,
                                BigDecimal revenue, BigDecimal cancellationRate) {
    }

    /** order-service: GET /api/v1/admin/orders/stats/customers */
    public record CustomerOrderStats(LocalDate fromDate, LocalDate toDate, String currency, long customersWithOrders,
                                     long orders, BigDecimal ordersPerCustomer, Map<String, Long> orderFrequency,
                                     List<CustomerRow> topBySpend) {
    }

    public record CustomerRow(Long customerId, String customerName, long orders, long completed, BigDecimal spent) {
    }

    /** order-service: GET /api/v1/admin/orders/stats/locations */
    public record LocationStats(LocalDate fromDate, LocalDate toDate, String currency, List<CityRow> cities) {
    }

    public record CityRow(String city, long orders, long completed, BigDecimal revenue) {
    }

    /** payment-service: GET /api/v1/admin/payments/summary */
    public record PaymentSummary(LocalDate fromDate, LocalDate toDate, Map<String, PaymentTotals> byStatus,
                                 Map<String, PaymentTotals> byMethod, BigDecimal collectedAmount,
                                 BigDecimal refundedAmount, String currency) {

        public long count(String status) {
            PaymentTotals totals = byStatus == null ? null : byStatus.get(status);
            return totals == null ? 0 : totals.count();
        }
    }

    public record PaymentTotals(long count, BigDecimal amount) {
    }

    /** delivery-service: GET /api/v1/admin/deliveries/summary */
    public record DeliveryReport(LocalDate fromDate, LocalDate toDate, Map<String, Long> byStatus, long total,
                                 long delivered, long cancelled, Double averageDeliveryMinutes, long delayedNow) {
    }

    /** delivery-service: GET /api/v1/admin/delivery-partners/stats */
    public record PartnerStats(LocalDate fromDate, LocalDate toDate, Map<String, Long> partnersNow, long online,
                               long waitingForPartner, List<PartnerRow> topPartners) {
    }

    public record PartnerRow(Long userId, String name, long delivered, Double averageDeliveryMinutes, long offersMissed) {
    }

    /** Every service: POST /api/v1/internal/audit-logs/events/republish */
    public record AuditReplay(String service, long republished) {
    }

    /** order-service: GET /api/v1/admin/orders/stats/calendar (only days with orders) */
    public record OrderCalendar(List<OrderCalendarDay> days) {
    }

    public record OrderCalendarDay(LocalDate date, long placed, long delivered, long cancelled) {
    }

    /** restaurant-service: GET /api/v1/admin/restaurants/stats/registrations (only days with registrations) */
    public record RegistrationCalendar(List<RegistrationDay> days) {
    }

    public record RegistrationDay(LocalDate date, long registered) {
    }

    /** promotion-service: GET /api/v1/admin/coupons (one page; only what the calendar needs) */
    public record CouponPage(List<CouponRow> content, boolean last) {
    }

    public record CouponRow(Long id, String code, String status, LocalDate validFrom, LocalDate validUntil,
                            int timesUsed) {
    }
}
