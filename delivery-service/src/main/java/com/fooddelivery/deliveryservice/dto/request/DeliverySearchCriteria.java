package com.fooddelivery.deliveryservice.dto.request;

import java.time.LocalDate;

import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

/** Admin delivery search filters; all optional. */
public record DeliverySearchCriteria(

        @Parameter(description = "PENDING, ASSIGNED, ACCEPTED, PICKED_UP, DELIVERED or CANCELLED")
        DeliveryStatus status,

        @Parameter(description = "The delivery of one order")
        Long orderId,

        @Parameter(description = "Deliveries of one partner")
        Long partnerUserId,

        @Parameter(description = "Deliveries to one customer")
        Long customerId,

        @Parameter(description = "Deliveries from one restaurant")
        Long restaurantId,

        @Parameter(description = "Pickup city", example = "Bengaluru")
        String city,

        @Parameter(example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Parameter(example = "2026-10-07")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate
) {
}
