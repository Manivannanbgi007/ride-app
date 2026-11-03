package com.fooddelivery.deliverypartnerservice.repository;

import java.util.Optional;

import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long>,
        JpaSpecificationExecutor<DeliveryPartner> {

    /**
     * The partner, locked until the transaction ends: a status report from delivery-service and an admin's change
     * to the same partner wait for each other instead of failing on the version.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DeliveryPartner p where p.userId = :userId")
    Optional<DeliveryPartner> lockById(@Param("userId") Long userId);
}
