package com.fooddelivery.orderservice.controller;

import java.util.List;

import com.fooddelivery.orderservice.dto.request.OrderSearchCriteria;
import com.fooddelivery.orderservice.dto.request.ReasonRequest;
import com.fooddelivery.orderservice.dto.response.AuditLogResponse;
import com.fooddelivery.orderservice.dto.response.CustomerOrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.OrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.PageResponse;
import com.fooddelivery.orderservice.service.AdminOrderService;
import com.fooddelivery.orderservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform admins: all orders, timelines and interventions. Requires an access token with the role PLATFORM_ADMIN. */
@Tag(name = "Admin - Orders", description = "Order monitoring and intervention")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(summary = "Search orders",
            description = "Examples: ?orderId=42  ?customerId=7  ?restaurantId=3&status=PLACED  "
                    + "?fromDate=2026-10-01&toDate=2026-10-07&page=0&size=20")
    @GetMapping
    public PageResponse<OrderSummaryResponse> searchOrders(
            @Valid @ParameterObject OrderSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminOrderService.searchOrders(criteria, pageable);
    }

    @Operation(summary = "Order details: items, prices, payment status, cancellation reason and timeline")
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        return adminOrderService.getOrder(orderId);
    }

    @Operation(summary = "Order timeline")
    @GetMapping("/{orderId}/timeline")
    public List<OrderResponse.TimelineEntry> getTimeline(@PathVariable Long orderId) {
        return adminOrderService.getTimeline(orderId);
    }

    @Operation(summary = "Cancel an order (intervention, audited)")
    @PutMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long orderId, @Valid @RequestBody ReasonRequest request,
                                     CallerInfo caller) {
        return adminOrderService.cancelOrder(orderId, request.reason(), caller);
    }

    @Operation(summary = "A customer's order summary (for the admin user screen)")
    @GetMapping("/customers/{customerId}/summary")
    public CustomerOrderSummaryResponse getCustomerSummary(@PathVariable Long customerId) {
        return adminOrderService.getCustomerSummary(customerId);
    }

    @Operation(summary = "Audit history of an order (newest first)")
    @GetMapping("/{orderId}/audit-logs")
    public PageResponse<AuditLogResponse> getOrderAuditLogs(@PathVariable Long orderId,
                                                            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminOrderService.getOrderAuditLogs(orderId, pageable);
    }
}
