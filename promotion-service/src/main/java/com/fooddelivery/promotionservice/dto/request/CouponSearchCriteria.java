package com.fooddelivery.promotionservice.dto.request;

import java.time.LocalDate;

import com.fooddelivery.promotionservice.entity.CouponStatus;
import com.fooddelivery.promotionservice.entity.DiscountType;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Admin coupon search filters; all optional. activeFrom and activeTo together find the coupons whose validity dates
 * overlap a period (start before the period, end after it, or inside it); either one alone is an open-ended period.
 */
public record CouponSearchCriteria(

        @Parameter(description = "Part of the code", example = "WELCOME")
        @Size(max = 30)
        String code,

        @Parameter(description = "ACTIVE or INACTIVE")
        CouponStatus status,

        @Parameter(description = "PERCENTAGE or FIXED")
        DiscountType discountType,

        @Parameter(description = "Coupons of one restaurant")
        Long restaurantId,

        @Parameter(description = "Valid on this day according to their dates", example = "2026-10-07")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate validOn,

        @Parameter(description = "Valid on at least one day of the period starting here (dates overlap the period)",
                example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate activeFrom,

        @Parameter(description = "Last day of that period, included (with activeFrom: the calendar's range)",
                example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate activeTo
) {
}
