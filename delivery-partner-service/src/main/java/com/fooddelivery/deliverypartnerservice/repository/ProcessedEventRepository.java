package com.fooddelivery.deliverypartnerservice.repository;

import java.time.Instant;

import com.fooddelivery.deliverypartnerservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query("delete from ProcessedEvent e where e.processedAt < :before")
    int deleteProcessedBefore(@Param("before") Instant before);
}
