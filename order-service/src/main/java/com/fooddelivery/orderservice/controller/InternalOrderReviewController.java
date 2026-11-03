package com.fooddelivery.orderservice.controller;

import com.fooddelivery.orderservice.dto.response.ReviewInfoResponse;
import com.fooddelivery.orderservice.service.OrderReviewInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class InternalOrderReviewController {

    private final OrderReviewInfoService orderReviewInfoService;

    @Operation(summary = "Owner, status and dishes of an order, for review-service")
    @GetMapping("/{orderId}/review-info")
    public ReviewInfoResponse getReviewInfo(@PathVariable Long orderId) {
        return orderReviewInfoService.getReviewInfo(orderId);
    }
}
