package com.fooddelivery.catalogservice.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fooddelivery.catalogservice.cache.MenuCache;
import com.fooddelivery.catalogservice.client.RestaurantSummary;
import com.fooddelivery.catalogservice.dto.response.MenuResponse;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.mapper.MenuItemMapper;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The menu customers see: only ACTIVE items of ACTIVE restaurants, grouped by category.
 * Sold-out items are included with available=false, so the app can show them greyed out.
 */
@Service
@RequiredArgsConstructor
public class PublicMenuService {

    private final RestaurantAccessGuard restaurantAccessGuard;
    private final MenuItemRepository menuItemRepository;
    private final CategoryLookup categoryLookup;
    private final MenuItemMapper menuItemMapper;
    private final MenuCache menuCache;

    /** Served from Redis when cached (see MenuCache), otherwise built from MySQL. */
    public MenuResponse getMenu(Long restaurantId) {
        return menuCache.menu(restaurantId, () -> buildMenu(restaurantId));
    }

    private MenuResponse buildMenu(Long restaurantId) {
        RestaurantSummary restaurant = restaurantAccessGuard.requireActiveRestaurant(restaurantId);

        List<MenuItem> items = menuItemRepository.findByRestaurantIdAndStatus(restaurantId, MenuItemStatus.ACTIVE);
        Map<Long, List<MenuItem>> itemsByCategory = items.stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId));
        Map<Long, Category> categories = categoryLookup.byIds(itemsByCategory.keySet());

        List<MenuResponse.Section> sections = itemsByCategory.entrySet().stream()
                .sorted(Comparator.comparing(entry -> categories.get(entry.getKey()).getDisplayOrder()))
                .map(entry -> new MenuResponse.Section(
                        entry.getKey(),
                        categories.get(entry.getKey()).getName(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(MenuItem::getName, String.CASE_INSENSITIVE_ORDER))
                                .map(menuItemMapper::toMenuEntry)
                                .toList()))
                .toList();

        return new MenuResponse(restaurantId, restaurant.name(), sections);
    }
}
