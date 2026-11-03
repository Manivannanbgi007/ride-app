package com.fooddelivery.deliveryservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the "app" prefix (see application.yml).
 *
 * @param businessTimeZone       time zone of "today" in the admin summary
 * @param deliveryPartnerService delivery-partner-service, asked about partners whose DeliveryPartnerChanged event
 *                               has not arrived
 * @param restaurantService      restaurant-service, asked for pickup addresses this service has no copy of
 * @param orderService           order-service, asked for delivery details of orders this service has no copy of
 * @param assignment             finding partners for waiting deliveries
 * @param delays                 when admins see a delivery as delayed
 * @param liveLocation           the partner's GPS position shared while a delivery is on its way
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("Asia/Kolkata") String businessTimeZone,
                            @DefaultValue RemoteService deliveryPartnerService,
                            @DefaultValue RemoteService restaurantService,
                            @DefaultValue RemoteService orderService,
                            @DefaultValue Assignment assignment,
                            @DefaultValue Delays delays,
                            @DefaultValue LiveLocation liveLocation) {

    /**
     * @param minInterval     positions closer together than this are not stored (a phone may report every second)
     * @param cleanupInterval how often the positions of finished or reassigned deliveries are deleted
     */
    public record LiveLocation(@DefaultValue("2s") Duration minInterval,
                               @DefaultValue("1m") Duration cleanupInterval) {
    }

    /** Always set timeouts on remote calls, so a slow service cannot block a request or the assignment job. */
    public record RemoteService(String baseUrl,
                                @DefaultValue("2s") Duration connectTimeout,
                                @DefaultValue("3s") Duration readTimeout) {
    }

    /**
     * @param enabled       run the assignment job (switched off in tests that run it themselves)
     * @param batchSize     deliveries handled per job run
     * @param acceptTimeout a partner who does not accept within this time loses the delivery and goes offline
     */
    public record Assignment(@DefaultValue("true") boolean enabled,
                             @DefaultValue("20") int batchSize,
                             @DefaultValue("2m") Duration acceptTimeout) {
    }

    /**
     * @param waitingFor    a delivery still without a partner this long after the food was ready
     * @param deliveringFor a delivery not delivered this long after the food was ready
     */
    public record Delays(@DefaultValue("10m") Duration waitingFor,
                         @DefaultValue("45m") Duration deliveringFor) {
    }
}
