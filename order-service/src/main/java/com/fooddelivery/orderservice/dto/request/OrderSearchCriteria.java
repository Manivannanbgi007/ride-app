package com.fooddelivery.orderservice.dto.request;

import java.time.LocalDate;

import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Optional order filters, bound from query parameters. Dates are calendar days in the business time zone;
 * toDate is inclusive (fromDate=2026-10-01&toDate=2026-10-07 is one full week).
 * Customers and restaurants use a subset; their own id is always applied by the service.
 */
public record OrderSearchCriteria(

        @Parameter(description = "Admin only: exact order id")
        Long orderId,

        @Parameter(description = "Admin only: orders of one customer")
        Long customerId,

        @Parameter(description = "Admin only: orders of one restaurant")
        Long restaurantId,

        @Parameter(description = "Order status")
        OrderStatus status,

        @Parameter(description = "Admin only: payment status")
        PaymentStatus paymentStatus,

        @Parameter(description = "First day, inclusive", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Parameter(description = "Last day, inclusive", example = "2026-10-07")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate
) {
}
