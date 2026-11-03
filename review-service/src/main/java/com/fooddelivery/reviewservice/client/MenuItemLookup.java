package com.fooddelivery.reviewservice.client;

import java.util.Collection;
import java.util.Map;

/** Asks catalog-service for current menu item data. An interface, so tests can replace the remote call. */
public interface MenuItemLookup {

    /**
     * Never fails: when catalog-service is down the answer is simply empty and callers use their own snapshots
     * (the data only decorates the featured dishes, it must never break the page). Unknown ids are left out.
     *
     * @return the items found, by menu item id
     */
    Map<Long, MenuItemInfo> findMenuItems(Collection<Long> ids);
}
