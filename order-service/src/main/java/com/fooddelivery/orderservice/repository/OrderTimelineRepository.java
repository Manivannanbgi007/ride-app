package com.fooddelivery.orderservice.repository;

import java.util.List;

import com.fooddelivery.orderservice.entity.OrderTimelineEntry;
import org.springframework.data.repository.Repository;

/** Append-only: save and read, no update or delete. */
public interface OrderTimelineRepository extends Repository<OrderTimelineEntry, Long> {

    OrderTimelineEntry save(OrderTimelineEntry entry);

    List<OrderTimelineEntry> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
