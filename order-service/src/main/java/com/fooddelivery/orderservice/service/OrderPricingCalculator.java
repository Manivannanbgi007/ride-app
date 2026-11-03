package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.response.PriceBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Price rules (configurable in application.yml):
 * <ul>
 *   <li>a coupon discount comes off the items first; it is never more than the items cost</li>
 *   <li>delivery fee, waived when the items after the discount reach the free-delivery threshold</li>
 *   <li>tax as a percentage of the items after the discount, rounded to 2 decimals (half up)</li>
 * </ul>
 * Money is always BigDecimal: double cannot represent amounts like 0.10 exactly.
 */
@Component
@RequiredArgsConstructor
public class OrderPricingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AppProperties appProperties;

    public PriceBreakdown calculate(BigDecimal itemsTotal) {
        return calculate(itemsTotal, BigDecimal.ZERO);
    }

    public PriceBreakdown calculate(BigDecimal itemsTotal, BigDecimal discount) {
        AppProperties.Pricing pricing = appProperties.pricing();
        BigDecimal items = itemsTotal.setScale(2, RoundingMode.HALF_UP);

        if (items.signum() == 0) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            return new PriceBreakdown(zero, zero, zero, zero, zero, pricing.currency());
        }

        BigDecimal discountAmount = discount.setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO).min(items);
        BigDecimal payableItems = items.subtract(discountAmount);
        BigDecimal deliveryFee = payableItems.compareTo(pricing.freeDeliveryThreshold()) >= 0
                ? BigDecimal.ZERO.setScale(2)
                : pricing.deliveryFee().setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = payableItems.multiply(pricing.taxPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = payableItems.add(deliveryFee).add(tax);

        return new PriceBreakdown(items, discountAmount, deliveryFee, tax, total, pricing.currency());
    }
}
