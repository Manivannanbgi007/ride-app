package com.fooddelivery.deliveryservice.repository;

import java.util.List;
import java.util.Set;

import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.RiderLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RiderLocationRepository extends JpaRepository<RiderLocation, Long> {

    /**
     * The positions nobody may see any more: the delivery is no longer on its way (delivered, cancelled, back in the
     * queue) or now belongs to another partner. They are then deleted with deleteAllByIdInBatch.
     */
    @Query("""
            select location.deliveryId from RiderLocation location, Delivery delivery
            where delivery.id = location.deliveryId
              and (delivery.status not in :onTheWay
                   or delivery.partnerUserId is null
                   or delivery.partnerUserId <> location.partnerUserId)
            """)
    List<Long> findNotOnTheWay(Set<DeliveryStatus> onTheWay);
}
