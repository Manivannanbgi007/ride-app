package com.fooddelivery.catalogservice.dto.response;

/**
 * Menu item numbers right now, for the admin dashboard.
 *
 * @param active   listed on menus (ACTIVE), whether in stock or not
 * @param disabled taken off the menu by the owner or an admin
 * @param orderable ACTIVE and in stock: what customers can order now
 * @param soldOut  ACTIVE but marked unavailable
 */
public record MenuItemStatsResponse(long total, long active, long disabled, long orderable, long soldOut) {
}
