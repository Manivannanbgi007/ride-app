package com.fooddelivery.deliveryservice.repository;

import com.fooddelivery.deliveryservice.entity.PartnerStatus;

/** One row of {@link DeliveryPartnerRepository#countByStatus()}. */
public interface PartnerStatusTotal {

    PartnerStatus getStatus();

    long getTotal();
}
