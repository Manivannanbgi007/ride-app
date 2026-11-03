package com.fooddelivery.promotionservice.discount;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.DiscountType;
import org.springframework.stereotype.Component;

/** A fixed amount off ("100 off"), never more than the items total. */
@Component
public class FixedDiscount implements DiscountStrategy {

    @Override
    public DiscountType type() {
        return DiscountType.FIXED;
    }

    @Override
    public BigDecimal discountFor(Coupon coupon, BigDecimal itemsTotal) {
        return coupon.getDiscountValue().min(itemsTotal).setScale(2, RoundingMode.HALF_UP);
    }
}
