package com.fooddelivery.promotionservice.controller;

import java.util.List;

import com.fooddelivery.promotionservice.dto.response.OfferResponse;
import com.fooddelivery.promotionservice.service.OfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public: no login needed. A coupon is applied to the cart in order-service (PUT /api/v1/cart/coupon). */
@Tag(name = "Offers", description = "Coupons customers can use today")
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @Operation(summary = "Coupons usable today", description = "With restaurantId also that restaurant's own coupons")
    @GetMapping
    public List<OfferResponse> currentOffers(@RequestParam(required = false) Long restaurantId) {
        return offerService.currentOffers(restaurantId);
    }
}
