package com.fooddelivery.deliveryservice.controller;

import com.fooddelivery.deliveryservice.dto.response.LiveTrackingResponse;
import com.fooddelivery.deliveryservice.dto.response.TrackingResponse;
import com.fooddelivery.deliveryservice.service.TrackingService;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer: delivery tracking", description = "Follow the delivery of your own order (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/delivery/orders")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @Operation(summary = "Where is my food?",
            description = "Status, partner and milestones. 404 until the restaurant has the food ready.")
    @GetMapping("/{orderId}")
    public TrackingResponse track(CallerInfo caller, @PathVariable Long orderId) {
        return trackingService.track(caller, orderId);
    }

    @Operation(summary = "Live map: where is my delivery partner right now?",
            description = "The partner's real GPS position (rider: null until their phone sends one) and the two "
                    + "addresses (location: null without map coordinates). No route, distance or arrival time. "
                    + "404 until a partner has accepted the delivery.")
    @GetMapping("/{orderId}/live")
    public LiveTrackingResponse live(CallerInfo caller, @PathVariable Long orderId) {
        return trackingService.live(caller, orderId);
    }
}
