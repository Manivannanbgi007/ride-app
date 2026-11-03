package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.response.PriceBreakdown;
import org.junit.jupiter.api.Test;

class OrderPricingCalculatorTest {

    private final OrderPricingCalculator calculator = new OrderPricingCalculator(new AppProperties("Asia/Kolkata",
            new AppProperties.Pricing("INR", new BigDecimal("40.00"), new BigDecimal("499.00"), new BigDecimal("5")),
            null, null));

    @Test
    void smallOrderPaysDeliveryFeeAndTax() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("299.00"));

        assertThat(price.deliveryFee()).isEqualByComparingTo("40.00");
        assertThat(price.taxAmount()).isEqualByComparingTo("14.95");   // 5% of 299.00
        assertThat(price.totalAmount()).isEqualByComparingTo("353.95");
        assertThat(price.currency()).isEqualTo("INR");
    }

    @Test
    void deliveryIsFreeFromTheThresholdUpwards() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("499.00"));

        assertThat(price.deliveryFee()).isEqualByComparingTo("0.00");
        assertThat(price.totalAmount()).isEqualByComparingTo("523.95");
    }

    @Test
    void taxIsRoundedHalfUpToPaise() {
        // 5% of 10.10 = 0.505 -> 0.51
        assertThat(calculator.calculate(new BigDecimal("10.10")).taxAmount()).isEqualByComparingTo("0.51");
    }

    @Test
    void emptyCartCostsNothing() {
        PriceBreakdown price = calculator.calculate(BigDecimal.ZERO);

        assertThat(price.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(price.deliveryFee()).isEqualByComparingTo("0.00");
        assertThat(price.discountAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void aCouponDiscountComesOffTheItemsBeforeTaxAndTheFreeDeliveryThreshold() {
        // 598.00 - 100.00 = 498.00: just under the 499.00 threshold, so delivery is charged; tax on 498.00
        PriceBreakdown price = calculator.calculate(new BigDecimal("598.00"), new BigDecimal("100.00"));

        assertThat(price.itemsTotal()).isEqualByComparingTo("598.00");
        assertThat(price.discountAmount()).isEqualByComparingTo("100.00");
        assertThat(price.deliveryFee()).isEqualByComparingTo("40.00");
        assertThat(price.taxAmount()).isEqualByComparingTo("24.90");
        assertThat(price.totalAmount()).isEqualByComparingTo("562.90");
    }

    @Test
    void aDiscountNeverTakesMoreThanTheItemsCost() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("79.00"), new BigDecimal("100.00"));

        assertThat(price.discountAmount()).isEqualByComparingTo("79.00");
        assertThat(price.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(price.totalAmount()).isEqualByComparingTo("40.00"); // only the delivery fee is left
    }
}
