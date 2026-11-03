package com.fooddelivery.promotionservice.controller;

import com.fooddelivery.promotionservice.dto.request.CouponCheckRequest;
import com.fooddelivery.promotionservice.dto.request.ReservationRequest;
import com.fooddelivery.promotionservice.dto.response.CouponCheckResponse;
import com.fooddelivery.promotionservice.dto.response.ReservationResponse;
import com.fooddelivery.promotionservice.service.CouponRedemptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API for order-service. The API Gateway has no route for /api/v1/internal/**.
 * A coupon that cannot be used answers 422 with the reason in "detail", ready to show to the customer.
 */
@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalCouponController {

    private final CouponRedemptionService couponRedemptionService;

    @Operation(summary = "Would this coupon work for this cart, and for how much? (changes nothing)")
    @PostMapping("/coupons/check")
    public CouponCheckResponse check(@Valid @RequestBody CouponCheckRequest request) {
        return couponRedemptionService.check(request);
    }

    @Operation(summary = "Reserve one use of a coupon for a checkout",
            description = "The same reservationKey again returns the first reservation (retried checkout)")
    @PostMapping("/coupon-reservations")
    public ReservationResponse reserve(@Valid @RequestBody ReservationRequest request) {
        return couponRedemptionService.reserve(request);
    }

    @Operation(summary = "The checkout failed after the reservation: give the use back")
    @DeleteMapping("/coupon-reservations/{reservationKey}")
    public ResponseEntity<Void> release(@PathVariable String reservationKey) {
        couponRedemptionService.releaseReservation(reservationKey);
        return ResponseEntity.noContent().build();
    }
}
