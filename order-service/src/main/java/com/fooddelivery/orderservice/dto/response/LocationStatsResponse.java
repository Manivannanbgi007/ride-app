package com.fooddelivery.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Orders in a period per delivery city, most orders first. */
public record LocationStatsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String currency,
        List<City> cities
) {

    public record City(String city, long orders, long completed, BigDecimal revenue) {
    }
}
