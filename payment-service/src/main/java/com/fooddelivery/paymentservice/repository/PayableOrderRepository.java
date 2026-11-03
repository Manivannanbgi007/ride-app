package com.fooddelivery.paymentservice.repository;

import com.fooddelivery.paymentservice.entity.PayableOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayableOrderRepository extends JpaRepository<PayableOrder, Long> {
}
