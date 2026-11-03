package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;

import com.fooddelivery.orderservice.config.AppProperties;

/** The same settings as application.yml, for unit tests that do not start Spring. */
final class TestProperties {

    private TestProperties() {
    }

    static AppProperties appProperties() {
        return appProperties("Asia/Kolkata");
    }

    /** The same settings in another business time zone (daylight-saving tests). */
    static AppProperties appProperties(String businessTimeZone) {
        return new AppProperties(businessTimeZone,
                new AppProperties.Pricing("INR", new BigDecimal("40.00"), new BigDecimal("499.00"), new BigDecimal("5")),
                new AppProperties.Cart(50, 20),
                null);
    }
}
