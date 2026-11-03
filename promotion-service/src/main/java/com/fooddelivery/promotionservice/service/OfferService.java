package com.fooddelivery.promotionservice.service;

import java.time.LocalDate;
import java.util.List;

import com.fooddelivery.promotionservice.dto.response.OfferResponse;
import com.fooddelivery.promotionservice.entity.CouponAvailability;
import com.fooddelivery.promotionservice.entity.CouponStatus;
import com.fooddelivery.promotionservice.mapper.CouponMapper;
import com.fooddelivery.promotionservice.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The coupons customers can use today (the "Offers" page). */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final CouponRepository couponRepository;
    private final CouponRules couponRules;
    private final CouponMapper couponMapper;

    /** @param restaurantId also list that restaurant's own coupons (without it: platform-wide coupons only) */
    @Transactional(readOnly = true)
    public List<OfferResponse> currentOffers(Long restaurantId) {
        LocalDate today = couponRules.today();
        return couponRepository.findValidOn(CouponStatus.ACTIVE, today, restaurantId).stream()
                .filter(coupon -> coupon.availabilityOn(today) == CouponAvailability.AVAILABLE)
                .map(couponMapper::toOffer)
                .toList();
    }
}
