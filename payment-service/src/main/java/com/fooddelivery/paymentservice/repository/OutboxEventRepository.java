package com.fooddelivery.paymentservice.repository;

import java.time.Instant;
import java.util.List;

import com.fooddelivery.paymentservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * The oldest unpublished events, locked until the transaction ends. A second relay (another instance
     * of this service) waits for the lock instead of skipping ahead, so events are published in order.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE",
            nativeQuery = true)
    List<OutboxEvent> lockOldestUnpublished(@Param("limit") int limit);

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByIdAsc(String aggregateType, Long aggregateId);

    /** Events not sent to Kafka yet (metric outbox.events.waiting). */
    long countByPublishedAtIsNull();
}
