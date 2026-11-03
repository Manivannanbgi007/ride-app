package com.fooddelivery.promotionservice.discount;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.DiscountType;
import org.springframework.stereotype.Component;

/** Factory: picks the {@link DiscountStrategy} for a coupon's discount type. */
@Component
public class DiscountStrategyFactory {

    private final Map<DiscountType, DiscountStrategy> strategies = new EnumMap<>(DiscountType.class);

    public DiscountStrategyFactory(List<DiscountStrategy> available) {
        available.forEach(strategy -> strategies.put(strategy.type(), strategy));
        for (DiscountType type : DiscountType.values()) {
            if (!strategies.containsKey(type)) {
                throw new IllegalStateException("No DiscountStrategy for " + type);
            }
        }
    }

    public BigDecimal discountFor(Coupon coupon, BigDecimal itemsTotal) {
        return strategies.get(coupon.getDiscountType()).discountFor(coupon, itemsTotal);
    }
}
