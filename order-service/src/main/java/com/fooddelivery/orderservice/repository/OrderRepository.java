package com.fooddelivery.orderservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /** Idempotency: has this customer already placed an order with this key? */
    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    /** Customer access: another customer's order id simply is not found. */
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    /** Restaurant access: orders of another restaurant are not found. */
    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    /** Sales summary for a restaurant: counted and summed in the database, per status. */
    @Query("""
            select o.status as status, count(o) as orderCount, coalesce(sum(o.totalAmount), 0) as totalAmount
            from Order o
            where o.restaurantId = :restaurantId and o.createdAt >= :from and o.createdAt < :to
            group by o.status
            """)
    List<StatusTotals> sumByStatusForRestaurant(Long restaurantId, Instant from, Instant to);

    /** Order summary of one customer, per status (for the admin user screen). */
    @Query("""
            select o.status as status, count(o) as orderCount, coalesce(sum(o.totalAmount), 0) as totalAmount
            from Order o
            where o.customerId = :customerId
            group by o.status
            """)
    List<StatusTotals> sumByStatusForCustomer(Long customerId);

    /** The same for the orders a customer placed in [from, before) (their dashboard's period; idx_orders_customer_created). */
    @Query("""
            select o.status as status, count(o) as orderCount, coalesce(sum(o.totalAmount), 0) as totalAmount
            from Order o
            where o.customerId = :customerId and o.createdAt >= :from and o.createdAt < :before
            group by o.status
            """)
    List<StatusTotals> sumByStatusForCustomerBetween(Long customerId, Instant from, Instant before);

    @Query("select max(o.createdAt) from Order o where o.customerId = :customerId")
    Optional<Instant> findLastOrderTime(Long customerId);

    /** Ids of orders in a status since before a time, oldest first (unpaid-order expiry; uses idx_orders_status_created). */
    @Query("select o.id from Order o where o.status = :status and o.createdAt < :before order by o.createdAt")
    List<Long> findIdsByStatusCreatedBefore(OrderStatus status, Instant before, Pageable pageable);
}
