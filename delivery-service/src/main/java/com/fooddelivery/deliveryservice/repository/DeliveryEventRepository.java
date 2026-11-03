package com.fooddelivery.deliveryservice.repository;

import java.util.List;

import com.fooddelivery.deliveryservice.entity.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {

    List<DeliveryEvent> findByDeliveryIdOrderByIdAsc(Long deliveryId);
}
