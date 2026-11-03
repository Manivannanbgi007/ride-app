package com.fooddelivery.promotionservice.discount;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.DiscountType;
import org.springframework.stereotype.Component;

/** discountValue percent of the items total, at most maxDiscount when the coupon has one ("20% off, up to 150"). */
@Component
public class PercentageDiscount implements DiscountStrategy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public DiscountType type() {
        return DiscountType.PERCENTAGE;
    }

    @Override
    public BigDecimal discountFor(Coupon coupon, BigDecimal itemsTotal) {
        BigDecimal discount = itemsTotal.multiply(coupon.getDiscountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        if (coupon.getMaxDiscount() != null) {
            discount = discount.min(coupon.getMaxDiscount());
        }
        return discount.min(itemsTotal).setScale(2, RoundingMode.HALF_UP);
    }
}
