package com.fooddelivery.adminservice.client;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.CustomerOrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.DeliveryReport;
import com.fooddelivery.adminservice.client.RemoteRecords.LocationStats;
import com.fooddelivery.adminservice.client.RemoteRecords.MenuItemStats;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PartnerStats;
import com.fooddelivery.adminservice.client.RemoteRecords.PaymentSummary;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantPerformance;
import com.fooddelivery.adminservice.client.RemoteRecords.RestaurantStats;
import com.fooddelivery.adminservice.client.RemoteRecords.UserStats;
import com.fooddelivery.adminservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The statistics endpoints of the services, one method each (periods are inclusive calendar days). */
@Component
@RequiredArgsConstructor
public class StatsClient {

    private final PlatformClient platformClient;

    public UserStats userStats(LocalDate from, LocalDate to, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.USER, "/api/v1/admin/users/stats", period(from, to),
                UserStats.class, caller);
    }

    public RestaurantStats restaurantStats(LocalDate from, LocalDate to, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.RESTAURANT, "/api/v1/admin/restaurants/stats", period(from, to),
                RestaurantStats.class, caller);
    }

    public MenuItemStats menuItemStats(CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.CATALOG, "/api/v1/admin/menu-items/stats", Map.of(),
                MenuItemStats.class, caller);
    }

    public OrderStats orderStats(LocalDate from, LocalDate to, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.ORDER, "/api/v1/admin/orders/stats", period(from, to),
                OrderStats.class, caller);
    }

    public RestaurantPerformance restaurantPerformance(LocalDate from, LocalDate to, int limit, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.ORDER, "/api/v1/admin/orders/stats/restaurants",
                limited(from, to, limit), RestaurantPerformance.class, caller);
    }

    public CustomerOrderStats customerOrderStats(LocalDate from, LocalDate to, int limit, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.ORDER, "/api/v1/admin/orders/stats/customers",
                limited(from, to, limit), CustomerOrderStats.class, caller);
    }

    public LocationStats locationStats(LocalDate from, LocalDate to, int limit, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.ORDER, "/api/v1/admin/orders/stats/locations",
                limited(from, to, limit), LocationStats.class, caller);
    }

    public PaymentSummary paymentSummary(LocalDate from, LocalDate to, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.PAYMENT, "/api/v1/admin/payments/summary", period(from, to),
                PaymentSummary.class, caller);
    }

    public DeliveryReport deliveryReport(LocalDate from, LocalDate to, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.DELIVERY, "/api/v1/admin/deliveries/summary", period(from, to),
                DeliveryReport.class, caller);
    }

    public PartnerStats partnerStats(LocalDate from, LocalDate to, int limit, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.DELIVERY, "/api/v1/admin/delivery-partners/stats",
                limited(from, to, limit), PartnerStats.class, caller);
    }

    private static Map<String, Object> period(LocalDate from, LocalDate to) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("fromDate", from);
        query.put("toDate", to);
        return query;
    }

    private static Map<String, Object> limited(LocalDate from, LocalDate to, int limit) {
        Map<String, Object> query = period(from, to);
        query.put("limit", limit);
        return query;
    }
}
