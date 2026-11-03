package com.fooddelivery.deliveryservice.repository;

import com.fooddelivery.deliveryservice.entity.DeliveryStatus;

/** One row of {@link DeliveryRepository#countByStatus}. */
public interface DeliveryStatusTotal {

    DeliveryStatus getStatus();

    long getTotal();
}
