package com.fooddelivery.reviewservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How the public ratings are rounded: one decimal, half up (4.25 shows as 4.3). */
public final class RatingMath {

    private RatingMath() {
    }

    /** {@code BigDecimal.valueOf} reads the shortest text of the number, so 4.35 is 4.35 and not 4.3499999... */
    public static BigDecimal round(double average) {
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }
}
