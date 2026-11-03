package com.fooddelivery.promotionservice.mapper;

import java.time.LocalDate;

import com.fooddelivery.promotionservice.dto.response.CouponResponse;
import com.fooddelivery.promotionservice.dto.response.OfferResponse;
import com.fooddelivery.promotionservice.dto.response.RedemptionResponse;
import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.CouponRedemption;
import com.fooddelivery.promotionservice.service.CouponRules;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

    public CouponResponse toResponse(Coupon coupon, LocalDate today) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscount(),
                coupon.getMinOrderAmount(),
                CouponRules.summary(coupon),
                coupon.getRestaurantId(),
                coupon.getValidFrom(),
                coupon.getValidUntil(),
                coupon.getUsageLimit(),
                coupon.getPerCustomerLimit(),
                coupon.getTimesUsed(),
                coupon.getStatus(),
                coupon.availabilityOn(today),
                coupon.getCreatedBy(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt(),
                coupon.getVersion());
    }

    public OfferResponse toOffer(Coupon coupon) {
        return new OfferResponse(coupon.getCode(), coupon.getDescription(), CouponRules.summary(coupon),
                coupon.getMinOrderAmount(), coupon.getRestaurantId(), coupon.getValidUntil());
    }

    public RedemptionResponse toResponse(CouponRedemption redemption) {
        return new RedemptionResponse(
                redemption.getId(),
                redemption.getCouponCode(),
                redemption.getCustomerId(),
                redemption.getRestaurantId(),
                redemption.getOrderId(),
                redemption.getOrderAmount(),
                redemption.getDiscountAmount(),
                redemption.getStatus(),
                redemption.getReleaseReason(),
                redemption.getReservedAt(),
                redemption.getAppliedAt(),
                redemption.getRedeemedAt(),
                redemption.getReleasedAt());
    }
}
