package com.fooddelivery.adminservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The admin dashboard: platform figures (cards) and chart data for the last days.
 *
 * @param today       the business day ("today" is in the business time zone)
 * @param chartsFrom  first day of the charts (charts cover chartsFrom..today)
 * @param unavailable services that did not answer: their figures are null and their charts empty
 */
public record DashboardResponse(
        LocalDate today,
        LocalDate chartsFrom,
        String currency,
        Instant generatedAt,
        Summary summary,
        Charts charts,
        List<String> unavailable
) {

    /**
     * Figures for the cards; null when the service that owns them did not answer.
     *
     * @param inactiveRestaurants        deactivated or suspended
     * @param activeMenuItems            listed on menus (in stock or not)
     * @param pendingOrders              not finished right now (waiting for payment, at the restaurant or on the way)
     * @param todaysRevenue              total of today's orders that were delivered
     * @param totalRevenue               total of all delivered orders
     * @param todaysSuccessfulPayments   online payments that went through today (including ones refunded since)
     * @param activeDeliveryPartners     partners online right now (available or on a delivery)
     * @param pendingDeliveryAssignments deliveries ready but not accepted by a partner yet, right now
     */
    public record Summary(
            Long totalCustomers,
            Long totalRestaurants,
            Long activeRestaurants,
            Long inactiveRestaurants,
            Long pendingRestaurantApprovals,
            Long totalMenuItems,
            Long activeMenuItems,
            Long todaysOrders,
            Long todaysCompletedOrders,
            Long todaysCancelledOrders,
            Long pendingOrders,
            BigDecimal todaysRevenue,
            BigDecimal totalRevenue,
            Long todaysSuccessfulPayments,
            Long todaysFailedPayments,
            Long activeDeliveryPartners,
            Long pendingDeliveryAssignments
    ) {
    }

    /**
     * @param ordersPerDay          orders and revenue per day (every day, also without orders)
     * @param ordersByStatus        the period's orders by their status now
     * @param restaurantPerformance top restaurants of the period by revenue
     */
    public record Charts(
            List<DayPoint> ordersPerDay,
            Map<String, Long> ordersByStatus,
            PaymentOutcome paymentOutcome,
            List<RestaurantPoint> restaurantPerformance,
            DeliveryPerformance deliveryPerformance
    ) {
    }

    public record DayPoint(LocalDate date, long orders, long completed, long cancelled, BigDecimal revenue) {
    }

    /**
     * Online payments of the period: successful includes the ones refunded since.
     *
     * @param refunded       how many payments were refunded in full
     * @param refundedAmount the money returned on the period's payments, full and partial refunds, whatever the
     *                       order's status (null when the payment service does not say)
     */
    public record PaymentOutcome(long successful, long failed, long refunded, BigDecimal refundedAmount) {
    }

    public record RestaurantPoint(Long restaurantId, String restaurantName, long orders, long completed,
                                  BigDecimal revenue, BigDecimal cancellationRate) {
    }

    public record DeliveryPerformance(long delivered, long cancelled, Double averageDeliveryMinutes, long delayedNow,
                                      List<PartnerPoint> topPartners) {
    }

    public record PartnerPoint(Long userId, String name, long delivered, Double averageDeliveryMinutes) {
    }
}
