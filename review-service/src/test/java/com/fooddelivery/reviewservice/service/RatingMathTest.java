package com.fooddelivery.reviewservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** One decimal, half up. */
class RatingMathTest {

    @Test
    void halfIsRoundedUp() {
        assertThat(RatingMath.round(4.25)).isEqualByComparingTo("4.3");
        assertThat(RatingMath.round(4.35)).isEqualByComparingTo("4.4");
        assertThat(RatingMath.round(4.45)).isEqualByComparingTo("4.5");
        assertThat(RatingMath.round(1.05)).isEqualByComparingTo("1.1");
    }

    @Test
    void otherValuesGoToTheNearestTenth() {
        assertThat(RatingMath.round(4.3333)).isEqualByComparingTo("4.3");
        assertThat(RatingMath.round(4.6667)).isEqualByComparingTo("4.7");
        assertThat(RatingMath.round(4.2499)).isEqualByComparingTo("4.2");
        assertThat(RatingMath.round(5.0)).isEqualByComparingTo("5.0");
        assertThat(RatingMath.round(1.0)).isEqualByComparingTo("1.0");
    }

    @Test
    void theResultAlwaysHasOneDecimal() {
        assertThat(RatingMath.round(4.0)).isEqualTo(new BigDecimal("4.0"));
        assertThat(RatingMath.round(3.96)).isEqualTo(new BigDecimal("4.0"));
        assertThat(RatingMath.round(4.25).scale()).isEqualTo(1);
    }
}
