package com.fooddelivery.adminservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone time zone of the business: "today" and report periods are calendar days in it
 * @param callTimeout      how long the dashboard and reports wait for one service before showing its part as
 *                         unavailable (the calls run in parallel)
 * @param services         where and how to call the services that own the data
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue("5s") Duration callTimeout,
                            @DefaultValue Services services) {

    public record Services(@DefaultValue RemoteService user,
                           @DefaultValue RemoteService restaurant,
                           @DefaultValue RemoteService catalog,
                           @DefaultValue RemoteService order,
                           @DefaultValue RemoteService payment,
                           @DefaultValue RemoteService notification,
                           @DefaultValue RemoteService delivery,
                           @DefaultValue RemoteService deliveryPartner,
                           @DefaultValue RemoteService promotion) {
    }

    /** Always set timeouts on remote calls, so a slow service cannot block threads. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("4s") Duration readTimeout) {
    }
}
