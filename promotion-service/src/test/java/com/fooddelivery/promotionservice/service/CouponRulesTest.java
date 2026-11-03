package com.fooddelivery.promotionservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.fooddelivery.promotionservice.config.AppProperties;
import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.CouponStatus;
import com.fooddelivery.promotionservice.entity.DiscountType;
import org.junit.jupiter.api.Test;

/** When a coupon can be used, and what a customer is told when it cannot. */
class CouponRulesTest {

    // 2026-10-07 23:30 in Bengaluru (18:00 UTC): "today" is the 7th there
    private static final Instant NOW = Instant.parse("2026-10-07T18:00:00Z");

    private final CouponRules rules = new CouponRules(
            new AppProperties("Asia/Kolkata", new AppProperties.Reservations(false, java.time.Duration.ofMinutes(30))),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aValidCouponForABigEnoughOrderHasNoProblem() {
        assertThat(rules.problem(welcome100(), 10L, new BigDecimal("499.00"), 0)).isEmpty();
    }

    @Test
    void theCustomerIsToldHowMuchIsMissing() {
        assertThat(rules.problem(welcome100(), 10L, new BigDecimal("379.00"), 0))
                .contains("Add items worth 120.00 more to use this coupon (minimum order 499.00)");
    }

    @Test
    void datesAreCalendarDaysInTheBusinessTimeZone() {
        Coupon endsToday = welcome100();
        endsToday.setValidUntil(LocalDate.parse("2026-10-07"));
        assertThat(rules.problem(endsToday, 10L, new BigDecimal("499.00"), 0)).isEmpty();

        Coupon endedYesterday = welcome100();
        endedYesterday.setValidUntil(LocalDate.parse("2026-10-06"));
        assertThat(rules.problem(endedYesterday, 10L, new BigDecimal("499.00"), 0))
                .contains("This coupon expired on 2026-10-06");

        Coupon startsTomorrow = welcome100();
        startsTomorrow.setValidFrom(LocalDate.parse("2026-10-08"));
        assertThat(rules.problem(startsTomorrow, 10L, new BigDecimal("499.00"), 0))
                .contains("This coupon can be used from 2026-10-08");
    }

    @Test
    void limitsStatusAndRestaurant() {
        Coupon coupon = welcome100();
        assertThat(rules.problem(coupon, 10L, new BigDecimal("499.00"), 1)).contains("You have already used this coupon");

        coupon.setRestaurantId(99L);
        assertThat(rules.problem(coupon, 10L, new BigDecimal("499.00"), 0))
                .contains("This coupon is only valid at another restaurant");

        coupon.setStatus(CouponStatus.INACTIVE);
        assertThat(rules.problem(coupon, 99L, new BigDecimal("499.00"), 0)).contains("This coupon is not active");
    }

    @Test
    void summariesInWords() {
        Coupon percent = welcome100();
        percent.setDiscountType(DiscountType.PERCENTAGE);
        percent.setDiscountValue(new BigDecimal("20.00"));
        percent.setMaxDiscount(new BigDecimal("150"));

        assertThat(CouponRules.summary(welcome100())).isEqualTo("100.00 off");
        assertThat(CouponRules.summary(percent)).isEqualTo("20% off, up to 150.00");
    }

    private static Coupon welcome100() {
        Coupon coupon = new Coupon();
        coupon.setCode("WELCOME100");
        coupon.setDiscountType(DiscountType.FIXED);
        coupon.setDiscountValue(new BigDecimal("100.00"));
        coupon.setMinOrderAmount(new BigDecimal("499.00"));
        coupon.setValidFrom(LocalDate.parse("2026-10-01"));
        coupon.setValidUntil(LocalDate.parse("2026-10-31"));
        coupon.setUsageLimit(10_000);
        coupon.setPerCustomerLimit(1);
        coupon.setStatus(CouponStatus.ACTIVE);
        return coupon;
    }
}
