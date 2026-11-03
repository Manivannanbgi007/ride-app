package com.fooddelivery.catalogservice.service;

import java.util.List;

import com.fooddelivery.catalogservice.config.AppProperties;
import com.fooddelivery.catalogservice.dto.request.MenuItemRequest;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.exception.InvalidRequestException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.MenuItemChange;
import com.fooddelivery.catalogservice.mapper.MenuItemMapper;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The rules for creating and editing a menu item, shared by restaurant owners and platform admins.
 * Who may do it (ownership, suspended restaurant) is checked by the caller; the methods here
 * must run inside the caller's transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuItemEditor {

    private final MenuItemRepository menuItemRepository;
    private final CategoryLookup categoryLookup;
    private final MenuItemMapper menuItemMapper;
    private final AppProperties appProperties;
    private final CatalogEventPublisher catalogEventPublisher;

    /** Adds an item (available) with the given starting status. */
    public MenuItemResponse create(Long restaurantId, MenuItemRequest request, MenuItemStatus status) {
        int maxItems = appProperties.menu().maxItemsPerRestaurant();
        if (menuItemRepository.countByRestaurantId(restaurantId) >= maxItems) {
            throw new BusinessRuleException("A restaurant can have at most " + maxItems + " menu items");
        }
        ensureUniqueName(restaurantId, request.name().trim(), null);
        Category category = categoryLookup.requireAssignable(request.categoryId());

        MenuItem item = new MenuItem();
        item.setRestaurantId(restaurantId);
        menuItemMapper.applyRequest(item, request);
        item.setStatus(status);
        item.setAvailable(true);

        MenuItem savedItem = menuItemRepository.saveAndFlush(item);
        catalogEventPublisher.menuItemChanged(savedItem, MenuItemChange.CREATED);
        log.info("Menu item created. restaurantId={} menuItemId={}", restaurantId, savedItem.getId());
        return menuItemMapper.toResponse(savedItem, category);
    }

    /** Changes the editable fields; status and availability stay as they are. */
    public MenuItemResponse update(MenuItem item, MenuItemRequest request) {
        if (request.version() == null) {
            throw new InvalidRequestException("version is required when updating a menu item");
        }
        ensureSameVersion(item, request.version());
        ensureUniqueName(item.getRestaurantId(), request.name().trim(), item.getId());
        // Moving to another category requires an ACTIVE one; keeping the current category is always fine.
        if (!item.getCategoryId().equals(request.categoryId())) {
            categoryLookup.requireAssignable(request.categoryId());
        }

        menuItemMapper.applyRequest(item, request);
        log.info("Menu item updated. restaurantId={} menuItemId={}", item.getRestaurantId(), item.getId());
        return saveAndPublish(item, MenuItemChange.UPDATED);
    }

    public void ensureSameVersion(MenuItem item, Long expectedVersion) {
        if (!item.getVersion().equals(expectedVersion)) {
            throw new ConflictException("The menu item was changed by another request (current version "
                    + item.getVersion() + ", yours " + expectedVersion + "). Reload it and try again.");
        }
    }

    /** Flushes first, so the event carries the new version (see CatalogEventPublisher). */
    public MenuItemResponse saveAndPublish(MenuItem item, MenuItemChange change) {
        MenuItem saved = menuItemRepository.saveAndFlush(item);
        catalogEventPublisher.menuItemChanged(saved, change);
        Category category = categoryLookup.byIds(List.of(saved.getCategoryId())).get(saved.getCategoryId());
        return menuItemMapper.toResponse(saved, category);
    }

    private void ensureUniqueName(Long restaurantId, String name, Long excludedItemId) {
        boolean taken = excludedItemId == null
                ? menuItemRepository.existsByRestaurantIdAndName(restaurantId, name)
                : menuItemRepository.existsByRestaurantIdAndNameAndIdNot(restaurantId, name, excludedItemId);
        if (taken) {
            throw new ConflictException("This restaurant already has a menu item named '" + name + "'");
        }
    }
}
