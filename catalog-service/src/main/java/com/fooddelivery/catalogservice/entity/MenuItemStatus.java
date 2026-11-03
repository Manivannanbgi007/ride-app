package com.fooddelivery.catalogservice.entity;

/**
 * Whether a menu item is listed. Separate from "available" (in stock today).
 */
public enum MenuItemStatus {
    /** Listed on the restaurant's menu. */
    ACTIVE,
    /** Hidden by the restaurant owner; the owner can enable it again. */
    DISABLED,
    /** Hidden by a platform admin (for example a policy problem); only an admin can reactivate it. */
    BLOCKED
}
