package com.fooddelivery.orderservice.controller;

import com.fooddelivery.orderservice.dto.response.InternalOrderResponse;
import com.fooddelivery.orderservice.service.OrderPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API for payment-service and delivery-service. The API Gateway has no route for /api/v1/internal/**.
 * Payment results no longer come in here: since Phase 8 they arrive as PaymentStatusChanged events.
 */
@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderPaymentService orderPaymentService;

    @Operation(summary = "Customer, amount, payment state and delivery details of an order",
            description = "Used by payment-service and delivery-service for orders they have not received "
                    + "an OrderCreated event for yet")
    @GetMapping("/{orderId}")
    public InternalOrderResponse getOrder(@PathVariable Long orderId) {
        return orderPaymentService.getInternalOrder(orderId);
    }
}
