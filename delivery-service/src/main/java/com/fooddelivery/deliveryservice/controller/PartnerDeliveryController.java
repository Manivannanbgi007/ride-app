package com.fooddelivery.deliveryservice.controller;

import com.fooddelivery.deliveryservice.dto.request.CompleteDeliveryRequest;
import com.fooddelivery.deliveryservice.dto.request.DeclineDeliveryRequest;
import com.fooddelivery.deliveryservice.dto.request.RiderLocationRequest;
import com.fooddelivery.deliveryservice.dto.response.DeliveryResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliverySummaryResponse;
import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.service.PartnerDeliveryService;
import com.fooddelivery.deliveryservice.service.RiderLocationService;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Delivery partner: deliveries", description = "The calling partner's deliveries (DELIVERY_PARTNER)")
@RestController
@RequestMapping("/api/v1/delivery/deliveries/mine")
@RequiredArgsConstructor
public class PartnerDeliveryController {

    private final PartnerDeliveryService partnerDeliveryService;
    private final RiderLocationService riderLocationService;

    @Operation(summary = "My delivery history", description = "Newest first; filter with ?status=DELIVERED")
    @GetMapping
    public PageResponse<DeliverySummaryResponse> getHistory(
            CallerInfo caller,
            @RequestParam(required = false) DeliveryStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return partnerDeliveryService.getHistory(caller, status, pageable);
    }

    @Operation(summary = "The delivery I am working on now", description = "204 No Content when there is none")
    @GetMapping("/current")
    public ResponseEntity<DeliveryResponse> getCurrent(CallerInfo caller) {
        return partnerDeliveryService.getCurrent(caller)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "One of my deliveries, with pickup and drop-off details and its timeline")
    @GetMapping("/{deliveryId}")
    public DeliveryResponse getDelivery(CallerInfo caller, @PathVariable Long deliveryId) {
        return partnerDeliveryService.getDelivery(caller, deliveryId);
    }

    @Operation(summary = "Accept the delivery offered to me")
    @PutMapping("/{deliveryId}/accept")
    public DeliveryResponse accept(CallerInfo caller, @PathVariable Long deliveryId) {
        return partnerDeliveryService.accept(caller, deliveryId);
    }

    @Operation(summary = "Decline the delivery offered to me", description = "It is offered to another partner")
    @PutMapping("/{deliveryId}/decline")
    public ResponseEntity<Void> decline(CallerInfo caller, @PathVariable Long deliveryId,
                                        @Valid @RequestBody(required = false) DeclineDeliveryRequest request) {
        partnerDeliveryService.decline(caller, deliveryId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "I have the food (picked up at the restaurant)")
    @PutMapping("/{deliveryId}/pickup")
    public DeliveryResponse pickUp(CallerInfo caller, @PathVariable Long deliveryId) {
        return partnerDeliveryService.pickUp(caller, deliveryId);
    }

    @Operation(summary = "Delivered to the customer", description = "Cash on delivery: confirm the amount in cashCollected")
    @PutMapping("/{deliveryId}/deliver")
    public DeliveryResponse deliver(CallerInfo caller, @PathVariable Long deliveryId,
                                    @Valid @RequestBody(required = false) CompleteDeliveryRequest request) {
        return partnerDeliveryService.deliver(caller, deliveryId, request);
    }

    @Operation(summary = "Share my live GPS position with the customer",
            description = "Only while the delivery is on its way (ACCEPTED or PICKED_UP), otherwise 409. Only the latest "
                    + "position is kept; it is deleted soon after the delivery is finished.")
    @PutMapping("/{deliveryId}/location")
    public ResponseEntity<Void> shareLocation(CallerInfo caller, @PathVariable Long deliveryId,
                                              @Valid @RequestBody RiderLocationRequest request) {
        riderLocationService.share(caller, deliveryId, request);
        return ResponseEntity.noContent().build();
    }
}
