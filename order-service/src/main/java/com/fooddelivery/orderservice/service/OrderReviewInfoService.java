package com.fooddelivery.orderservice.service;

import com.fooddelivery.orderservice.dto.response.ReviewInfoResponse;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The order lookup for review-service. Read-only: reviews never change an order. */
@Service
@RequiredArgsConstructor
public class OrderReviewInfoService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public ReviewInfoResponse getReviewInfo(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return new ReviewInfoResponse(order.getId(), order.getCustomerId(), order.getCustomerName(),
                order.getRestaurantId(), order.getRestaurantName(), order.getStatus(),
                order.getLines().stream()
                        .map(line -> new ReviewInfoResponse.Line(line.menuItemId(), line.name()))
                        .toList());
    }
}
