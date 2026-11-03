package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Revenue of one dish per month, week or day, every bucket of the window listed (zeros included), oldest first. */
public record MenuItemRevenueResponse(
        long menuItemId,
        String period,
        BigDecimal totalRevenue,
        long totalQuantity,
        List<Point> points
) {

    /** @param start first day of the bucket in the business time zone */
    public record Point(String label, LocalDate start, BigDecimal revenue, long quantity, long orders) {
    }
}
