package com.fooddelivery.orderservice.repository;

import java.util.Optional;

import com.fooddelivery.orderservice.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByCustomerId(Long customerId);
}
