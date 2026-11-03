package com.fooddelivery.orderservice.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Read-only statistics over orders for the admin dashboard and reports: counted and summed in the database,
 * never by loading orders. Revenue is the total of DELIVERED orders (what customers paid for food they got).
 */
public interface OrderStatsRepository extends Repository<Order, Long> {

    // Query fragments, joined with explicit spaces (annotation values must be constants)
    String IS_DELIVERED = "o.status = com.fooddelivery.orderservice.entity.OrderStatus.DELIVERED";
    String IS_CANCELLED = "o.status in (com.fooddelivery.orderservice.entity.OrderStatus.CANCELLED, "
            + "com.fooddelivery.orderservice.entity.OrderStatus.REJECTED)";
    String DELIVERED_COUNT = "sum(case when " + IS_DELIVERED + " then 1 else 0 end)";
    String CANCELLED_COUNT = "sum(case when " + IS_CANCELLED + " then 1 else 0 end)";
    String REVENUE = "coalesce(sum(case when " + IS_DELIVERED + " then o.totalAmount end), 0)";
    String IN_PERIOD = "o.createdAt >= :from and o.createdAt < :before";
    /** Orders the restaurant got to see: cash on delivery, or paid online (refunded ones were paid first). */
    String REACHED_RESTAURANT = "(o.paymentMethod = com.fooddelivery.orderservice.entity.PaymentMethod.CASH_ON_DELIVERY"
            + " or o.paymentStatus in (com.fooddelivery.orderservice.entity.PaymentStatus.PAID,"
            + " com.fooddelivery.orderservice.entity.PaymentStatus.REFUNDED))";
    String PER_RESTAURANT = "select o.restaurantId as restaurantId, max(o.restaurantName) as restaurantName,"
            + " count(o) as orderCount, " + DELIVERED_COUNT + " as deliveredCount, " + CANCELLED_COUNT + " as cancelledCount,"
            + " " + REVENUE + " as revenue"
            + " from Order o where " + IN_PERIOD + " and " + REACHED_RESTAURANT
            + " group by o.restaurantId";
    String TOTALS = "select o.status as status, o.paymentMethod as paymentMethod, count(o) as orderCount,"
            + " coalesce(sum(o.totalAmount), 0) as totalAmount, coalesce(sum(o.discountAmount), 0) as discountAmount"
            + " from Order o";

    /** Orders created in a period, per status and payment method. */
    @Query(TOTALS + " where " + IN_PERIOD + " group by o.status, o.paymentMethod")
    List<OrderTotals> sumPerStatusAndMethod(Instant from, Instant before);

    /** All orders ever, per status and payment method (the dashboard's "total" figures). */
    @Query(TOTALS + " group by o.status, o.paymentMethod")
    List<OrderTotals> sumPerStatusAndMethodAllTime();

    /**
     * Orders per calendar day and status. The UTC timestamps are shifted by the business time zone's UTC offset
     * (e.g. '+05:30') before taking the date; the caller passes a range in which that offset does not change.
     */
    @Query(value = """
            SELECT DATE_FORMAT(CONVERT_TZ(created_at, '+00:00', :utcOffset), '%Y-%m-%d') AS day, status AS status,
                   COUNT(*) AS orderCount, COALESCE(SUM(total_amount), 0) AS totalAmount
            FROM orders
            WHERE created_at >= :from AND created_at < :before
            GROUP BY day, status
            """, nativeQuery = true)
    List<DayTotals> sumPerDayAndStatus(Instant from, Instant before, String utcOffset);

    /**
     * The same per day and status for one restaurant, without the orders still waiting for their online payment (the
     * restaurant does not see those, and its sales summary does not count them). Same UTC offset rule.
     */
    @Query(value = """
            SELECT DATE_FORMAT(CONVERT_TZ(created_at, '+00:00', :utcOffset), '%Y-%m-%d') AS day, status AS status,
                   COUNT(*) AS orderCount, COALESCE(SUM(total_amount), 0) AS totalAmount
            FROM orders
            WHERE restaurant_id = :restaurantId AND status <> 'PAYMENT_PENDING'
              AND created_at >= :from AND created_at < :before
            GROUP BY day, status
            """, nativeQuery = true)
    List<DayTotals> sumPerDayAndStatusForRestaurant(Long restaurantId, Instant from, Instant before, String utcOffset);

    /**
     * The same rows per hour of the day (0-23 in the business time zone) and status, summed over every day of the
     * range: when a restaurant's orders come in. Same UTC offset rule, unpaid orders left out the same way.
     */
    @Query(value = """
            SELECT HOUR(CONVERT_TZ(created_at, '+00:00', :utcOffset)) AS hour, status AS status,
                   COUNT(*) AS orderCount, COALESCE(SUM(total_amount), 0) AS totalAmount
            FROM orders
            WHERE restaurant_id = :restaurantId AND status <> 'PAYMENT_PENDING'
              AND created_at >= :from AND created_at < :before
            GROUP BY hour, status
            """, nativeQuery = true)
    List<HourTotals> sumPerHourAndStatusForRestaurant(Long restaurantId, Instant from, Instant before,
                                                      String utcOffset);

    /**
     * The same per day and status for one customer's own orders, every status (their dashboard shows what they
     * placed, waiting for payment included). Same UTC offset rule; idx_orders_customer_created finds the orders.
     */
    @Query(value = """
            SELECT DATE_FORMAT(CONVERT_TZ(created_at, '+00:00', :utcOffset), '%Y-%m-%d') AS day, status AS status,
                   COUNT(*) AS orderCount, COALESCE(SUM(total_amount), 0) AS totalAmount
            FROM orders
            WHERE customer_id = :customerId AND created_at >= :from AND created_at < :before
            GROUP BY day, status
            """, nativeQuery = true)
    List<DayTotals> sumPerDayAndStatusForCustomer(Long customerId, Instant from, Instant before, String utcOffset);

    /**
     * The dishes a customer orders most, all time (their dashboard's "Order again"): most orders first, then most
     * portions, then the latest. Like topFoodsOfCustomer, the latest line of each dish gives its name and the
     * restaurant it came from. Cancelled, rejected and unpaid orders do not count.
     */
    @Query(value = """
            SELECT menu_item_id AS menuItemId, MAX(CASE WHEN rn = 1 THEN name END) AS name,
                   MAX(CASE WHEN rn = 1 THEN restaurant_id END) AS restaurantId,
                   MAX(CASE WHEN rn = 1 THEN restaurant_name END) AS restaurantName,
                   COUNT(DISTINCT order_id) AS orderCount, SUM(quantity) AS quantity, MAX(created_at) AS lastOrderedAt
            FROM (SELECT i.menu_item_id, i.name, i.order_id, i.quantity, o.restaurant_id, o.restaurant_name,
                         o.created_at,
                         ROW_NUMBER() OVER (PARTITION BY i.menu_item_id ORDER BY o.created_at DESC, i.id DESC) AS rn
                  FROM orders o JOIN order_items i ON i.order_id = o.id
                  WHERE o.customer_id = :customerId
                    AND o.status NOT IN ('CANCELLED', 'REJECTED', 'PAYMENT_PENDING')) ranked_lines
            GROUP BY menu_item_id
            ORDER BY COUNT(DISTINCT order_id) DESC, SUM(quantity) DESC, MAX(created_at) DESC, menu_item_id
            LIMIT :limit
            """, nativeQuery = true)
    List<FavouriteDishRow> favouriteDishesOfCustomer(long customerId, int limit);

    /**
     * One dish's order lines in orders of the given status (DELIVERED), per business day: revenue is the lines' own
     * totals (before coupon discount, delivery fee and tax), orders are distinct orders containing the dish.
     * Same UTC offset rule as {@link #sumPerDayAndStatus}.
     */
    @Query(value = """
            SELECT DATE_FORMAT(CONVERT_TZ(o.created_at, '+00:00', :utcOffset), '%Y-%m-%d') AS day,
                   COALESCE(SUM(i.line_total), 0) AS revenue, COALESCE(SUM(i.quantity), 0) AS quantity,
                   COUNT(DISTINCT o.id) AS orderCount
            FROM order_items i JOIN orders o ON o.id = i.order_id
            WHERE i.menu_item_id = :menuItemId AND o.status = :status
              AND o.created_at >= :from AND o.created_at < :before
            GROUP BY day
            """, nativeQuery = true)
    List<DishDayTotals> sumDishPerDay(long menuItemId, String status, Instant from, Instant before, String utcOffset);

    /**
     * A customer's dishes, most ordered first, in one statement. The window function marks each dish's latest line
     * so the latest name and unit price come out of the same grouped pass. The customer's orders are found by
     * idx_orders_customer_created (customer_id, created_at), then their lines by idx_order_items_order.
     * Orders that count: all except CANCELLED, REJECTED and PAYMENT_PENDING (never paid, never reached a kitchen).
     */
    @Query(value = """
            SELECT menu_item_id AS menuItemId, MAX(CASE WHEN rn = 1 THEN name END) AS name,
                   COUNT(DISTINCT order_id) AS orderCount, SUM(quantity) AS quantity, SUM(line_total) AS totalSpent,
                   MAX(CASE WHEN rn = 1 THEN unit_price END) AS unitPrice, MAX(created_at) AS lastOrderedAt
            FROM (SELECT i.menu_item_id, i.name, i.order_id, i.quantity, i.line_total, i.unit_price, o.created_at,
                         ROW_NUMBER() OVER (PARTITION BY i.menu_item_id ORDER BY o.created_at DESC, i.id DESC) AS rn
                  FROM orders o JOIN order_items i ON i.order_id = o.id
                  WHERE o.customer_id = :customerId AND o.created_at >= :from
                    AND o.status NOT IN ('CANCELLED', 'REJECTED', 'PAYMENT_PENDING')) ranked_lines
            GROUP BY menu_item_id
            ORDER BY SUM(quantity) DESC, COUNT(DISTINCT order_id) DESC, MAX(created_at) DESC, menu_item_id
            LIMIT :limit
            """, nativeQuery = true)
    List<FoodTotals> topFoodsOfCustomer(long customerId, Instant from, int limit);

    /**
     * A restaurant's dishes, most ordered first, in one grouped statement (the window function picks each dish's
     * latest name). The orders are found by idx_orders_restaurant_status_created (restaurant_id first), their
     * lines by idx_order_items_order. Orders that count are the ones the owner's sales-summary counts: every
     * status except PAYMENT_PENDING (unpaid orders the restaurant never saw).
     */
    @Query(value = """
            SELECT menu_item_id AS menuItemId, MAX(CASE WHEN rn = 1 THEN name END) AS name,
                   SUM(quantity) AS quantity, COUNT(DISTINCT order_id) AS orderCount, SUM(line_total) AS revenue
            FROM (SELECT i.menu_item_id, i.name, i.order_id, i.quantity, i.line_total,
                         ROW_NUMBER() OVER (PARTITION BY i.menu_item_id ORDER BY o.created_at DESC, i.id DESC) AS rn
                  FROM orders o JOIN order_items i ON i.order_id = o.id
                  WHERE o.restaurant_id = :restaurantId AND o.created_at >= :from AND o.created_at < :before
                    AND o.status <> 'PAYMENT_PENDING') ranked_lines
            GROUP BY menu_item_id
            ORDER BY SUM(quantity) DESC, COUNT(DISTINCT order_id) DESC, menu_item_id
            LIMIT :limit
            """, nativeQuery = true)
    List<RestaurantItemTotals> topItemsOfRestaurant(long restaurantId, Instant from, Instant before, int limit);

    @Query("select count(distinct o.restaurantId) from Order o where " + IN_PERIOD + " and " + REACHED_RESTAURANT)
    long countRestaurantsWithOrders(Instant from, Instant before);

    @Query(PER_RESTAURANT + " order by " + REVENUE + " desc, count(o) desc")
    List<RestaurantTotals> topRestaurantsByRevenue(Instant from, Instant before, Limit limit);

    @Query(PER_RESTAURANT + " order by " + REVENUE + " asc, count(o) asc")
    List<RestaurantTotals> lowestRestaurantsByRevenue(Instant from, Instant before, Limit limit);

    @Query(PER_RESTAURANT + " order by " + CANCELLED_COUNT + " desc, count(o) desc")
    List<RestaurantTotals> mostCancelledRestaurants(Instant from, Instant before, Limit limit);

    @Query("select count(distinct o.customerId) from Order o where " + IN_PERIOD)
    long countCustomersWithOrders(Instant from, Instant before);

    @Query("select o.customerId as customerId, max(o.customerName) as customerName, count(o) as orderCount,"
            + " " + DELIVERED_COUNT + " as deliveredCount, " + REVENUE + " as spent"
            + " from Order o where " + IN_PERIOD
            + " group by o.customerId order by " + REVENUE + " desc, count(o) desc")
    List<CustomerTotals> topCustomersBySpend(Instant from, Instant before, Limit limit);

    /** How many customers placed 1, 2-3, 4-5 or 6+ orders in a period. */
    @Query(value = """
            SELECT CASE WHEN per_customer.orders = 1 THEN '1'
                        WHEN per_customer.orders <= 3 THEN '2-3'
                        WHEN per_customer.orders <= 5 THEN '4-5'
                        ELSE '6+' END AS bucket,
                   COUNT(*) AS customers
            FROM (SELECT customer_id, COUNT(*) AS orders FROM orders
                  WHERE created_at >= :from AND created_at < :before
                  GROUP BY customer_id) per_customer
            GROUP BY bucket
            """, nativeQuery = true)
    List<FrequencyTotals> countCustomersPerOrderFrequency(Instant from, Instant before);

    @Query("select o.deliveryAddress.city as city, count(o) as orderCount, " + DELIVERED_COUNT + " as deliveredCount,"
            + " " + REVENUE + " as revenue"
            + " from Order o where " + IN_PERIOD
            + " group by o.deliveryAddress.city order by count(o) desc")
    List<CityTotals> sumPerCity(Instant from, Instant before, Limit limit);

    // ---- Query results (Spring Data projections) ----

    interface OrderTotals {
        OrderStatus getStatus();

        PaymentMethod getPaymentMethod();

        long getOrderCount();

        BigDecimal getTotalAmount();

        BigDecimal getDiscountAmount();
    }

    /** Native query: plain column values. */
    interface DayTotals {
        String getDay();

        String getStatus();

        long getOrderCount();

        BigDecimal getTotalAmount();
    }

    /** Native query: plain column values (hour 0-23). */
    interface HourTotals {
        int getHour();

        String getStatus();

        long getOrderCount();

        BigDecimal getTotalAmount();
    }

    /** Native query: plain column values. */
    interface DishDayTotals {
        String getDay();

        BigDecimal getRevenue();

        long getQuantity();

        long getOrderCount();
    }

    /** Native query: plain column values. */
    interface FoodTotals {
        long getMenuItemId();

        String getName();

        long getOrderCount();

        long getQuantity();

        BigDecimal getTotalSpent();

        BigDecimal getUnitPrice();

        Instant getLastOrderedAt();
    }

    /** Native query: plain column values. */
    interface FavouriteDishRow {
        long getMenuItemId();

        String getName();

        long getRestaurantId();

        String getRestaurantName();

        long getOrderCount();

        long getQuantity();

        Instant getLastOrderedAt();
    }

    /** Native query: plain column values. */
    interface RestaurantItemTotals {
        long getMenuItemId();

        String getName();

        long getQuantity();

        long getOrderCount();

        BigDecimal getRevenue();
    }

    interface RestaurantTotals {
        Long getRestaurantId();

        String getRestaurantName();

        long getOrderCount();

        long getDeliveredCount();

        long getCancelledCount();

        BigDecimal getRevenue();
    }

    interface CustomerTotals {
        Long getCustomerId();

        String getCustomerName();

        long getOrderCount();

        long getDeliveredCount();

        BigDecimal getSpent();
    }

    interface FrequencyTotals {
        String getBucket();

        long getCustomers();
    }

    interface CityTotals {
        String getCity();

        long getOrderCount();

        long getDeliveredCount();

        BigDecimal getRevenue();
    }
}
