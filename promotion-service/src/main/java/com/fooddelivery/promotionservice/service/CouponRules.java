package com.fooddelivery.promotionservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import com.fooddelivery.promotionservice.config.AppProperties;
import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.DiscountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * When a coupon can be used, and the reason (in words a customer understands) when it cannot. Used by the quote
 * shown in the cart and by the reservation at checkout, so both always agree.
 */
@Component
@RequiredArgsConstructor
public class CouponRules {

    private final AppProperties appProperties;
    private final Clock clock;

    /** Today in the business time zone: coupon dates are calendar days there. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.of(appProperties.businessTimeZone())));
    }

    /**
     * @param customerUses this customer's current uses of the coupon (reserved, on an order or redeemed)
     * @return why the coupon cannot be used for this order, or empty when it can
     */
    public Optional<String> problem(Coupon coupon, Long restaurantId, BigDecimal itemsTotal, long customerUses) {
        LocalDate today = today();
        switch (coupon.availabilityOn(today)) {
            case INACTIVE -> {
                return Optional.of("This coupon is not active");
            }
            case SCHEDULED -> {
                return Optional.of("This coupon can be used from " + coupon.getValidFrom());
            }
            case EXPIRED -> {
                return Optional.of("This coupon expired on " + coupon.getValidUntil());
            }
            case USED_UP -> {
                return Optional.of("This coupon has been fully used");
            }
            case AVAILABLE -> {
                // checked below
            }
        }
        if (coupon.getRestaurantId() != null && !coupon.getRestaurantId().equals(restaurantId)) {
            return Optional.of("This coupon is only valid at another restaurant");
        }
        if (itemsTotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            BigDecimal missing = coupon.getMinOrderAmount().subtract(itemsTotal).setScale(2, RoundingMode.HALF_UP);
            return Optional.of("Add items worth " + missing + " more to use this coupon (minimum order "
                    + coupon.getMinOrderAmount().setScale(2, RoundingMode.HALF_UP) + ")");
        }
        if (coupon.getPerCustomerLimit() != null && customerUses >= coupon.getPerCustomerLimit()) {
            return Optional.of(coupon.getPerCustomerLimit() == 1
                    ? "You have already used this coupon"
                    : "You have already used this coupon " + coupon.getPerCustomerLimit() + " times");
        }
        return Optional.empty();
    }

    /** The discount in words: "100.00 off", "20% off", "20% off, up to 150.00". */
    public static String summary(Coupon coupon) {
        if (coupon.getDiscountType() == DiscountType.FIXED) {
            return coupon.getDiscountValue().setScale(2, RoundingMode.HALF_UP) + " off";
        }
        String percent = coupon.getDiscountValue().stripTrailingZeros().toPlainString() + "% off";
        return coupon.getMaxDiscount() == null
                ? percent
                : percent + ", up to " + coupon.getMaxDiscount().setScale(2, RoundingMode.HALF_UP);
    }
}
