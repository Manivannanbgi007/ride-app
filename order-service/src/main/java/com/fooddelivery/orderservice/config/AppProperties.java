package com.fooddelivery.orderservice.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone time zone of the business, used for date filters such as fromDate=2026-10-07
 * @param pricing          delivery fee and tax rules
 * @param cart             cart limits
 * @param services         where and how to call other services
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue Pricing pricing,
                            @DefaultValue Cart cart,
                            @DefaultValue Services services) {

    public record Pricing(@DefaultValue("INR") String currency,
                          @DefaultValue("40.00") BigDecimal deliveryFee,
                          @DefaultValue("499.00") BigDecimal freeDeliveryThreshold,
                          @DefaultValue("5") BigDecimal taxPercent) {
    }

    public record Cart(@DefaultValue("50") int maxLines,
                       @DefaultValue("20") int maxQuantityPerItem) {
    }

    public record Services(@DefaultValue RemoteService user,
                           @DefaultValue RemoteService restaurant,
                           @DefaultValue RemoteService catalog,
                           @DefaultValue RemoteService promotion) {
    }

    /** Always set timeouts on remote calls, so a slow service cannot block request threads. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("3s") Duration readTimeout) {
    }
}
