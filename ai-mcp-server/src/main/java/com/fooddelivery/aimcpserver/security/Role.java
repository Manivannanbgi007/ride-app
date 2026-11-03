package com.fooddelivery.aimcpserver.security;

/** The platform roles; each one gets its own tool set (ToolCatalog) and system prompt (SystemPrompts). */
public enum Role {
    CUSTOMER,
    RESTAURANT_ADMIN,
    PLATFORM_ADMIN,
    DELIVERY_PARTNER
}
