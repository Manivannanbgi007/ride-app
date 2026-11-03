package com.fooddelivery.adminservice.client;

/** The services admin-service reads from (each owns its data and answers for it). */
public enum PlatformService {
    USER("user-service"),
    RESTAURANT("restaurant-service"),
    CATALOG("catalog-service"),
    ORDER("order-service"),
    PAYMENT("payment-service"),
    NOTIFICATION("notification-service"),
    DELIVERY("delivery-service"),
    DELIVERY_PARTNER("delivery-partner-service"),
    PROMOTION("promotion-service");

    private final String serviceName;

    PlatformService(String serviceName) {
        this.serviceName = serviceName;
    }

    /** As in spring.application.name, e.g. "order-service". */
    public String serviceName() {
        return serviceName;
    }
}
