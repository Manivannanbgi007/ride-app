package com.fooddelivery.promotionservice.discount;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.DiscountType;
import org.junit.jupiter.api.Test;

class DiscountStrategyFactoryTest {

    private final DiscountStrategyFactory factory =
            new DiscountStrategyFactory(List.of(new PercentageDiscount(), new FixedDiscount()));

    @Test
    void percentageIsWorkedOutOnTheItemsAndCapped() {
        Coupon twentyPercentUpTo150 = coupon(DiscountType.PERCENTAGE, "20", "150");

        assertThat(factory.discountFor(twentyPercentUpTo150, new BigDecimal("499.00"))).isEqualByComparingTo("99.80");
        assertThat(factory.discountFor(twentyPercentUpTo150, new BigDecimal("1000.00"))).isEqualByComparingTo("150.00");
    }

    @Test
    void percentageWithoutCap() {
        assertThat(factory.discountFor(coupon(DiscountType.PERCENTAGE, "15", null), new BigDecimal("333.33")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void fixedNeverTakesMoreThanTheItemsCost() {
        Coupon hundredOff = coupon(DiscountType.FIXED, "100", null);

        assertThat(factory.discountFor(hundredOff, new BigDecimal("599.00"))).isEqualByComparingTo("100.00");
        assertThat(factory.discountFor(hundredOff, new BigDecimal("79.00"))).isEqualByComparingTo("79.00");
    }

    private static Coupon coupon(DiscountType type, String value, String maxDiscount) {
        Coupon coupon = new Coupon();
        coupon.setDiscountType(type);
        coupon.setDiscountValue(new BigDecimal(value));
        coupon.setMaxDiscount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        return coupon;
    }
}
