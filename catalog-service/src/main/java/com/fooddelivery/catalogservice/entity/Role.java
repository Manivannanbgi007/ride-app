package com.fooddelivery.catalogservice.entity;

/** Platform roles, as issued by user-service. Used to read the "role" claim of the access token. */
public enum Role {
    CUSTOMER,
    RESTAURANT_ADMIN,
    DELIVERY_PARTNER,
    PLATFORM_ADMIN
}
