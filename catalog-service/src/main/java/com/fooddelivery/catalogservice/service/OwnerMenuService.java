package com.fooddelivery.catalogservice.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fooddelivery.catalogservice.dto.request.MenuItemRequest;
import com.fooddelivery.catalogservice.dto.request.PriceUpdateRequest;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.exception.InvalidRequestException;
import com.fooddelivery.catalogservice.exception.ResourceNotFoundException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.MenuItemChange;
import com.fooddelivery.catalogservice.mapper.MenuItemMapper;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A restaurant owner (RESTAURANT_ADMIN) managing the menu of their own restaurant.
 * <p>
 * Each method first asks restaurant-service whether the caller owns the restaurant (a remote call),
 * and only then opens a short database transaction with {@link TransactionTemplate}.
 * Doing the remote call outside the transaction means no database connection is held while waiting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerMenuService {

    private final RestaurantAccessGuard restaurantAccessGuard;
    private final MenuItemRepository menuItemRepository;
    private final CategoryLookup categoryLookup;
    private final MenuItemMapper menuItemMapper;
    private final TransactionTemplate transactionTemplate;
    private final MenuItemEditor menuItemEditor;

    /** The whole menu of the restaurant, every status, ordered by category then name. */
    public List<MenuItemResponse> getMenuItems(Long ownerUserId, Long restaurantId) {
        restaurantAccessGuard.requireOwnedRestaurant(restaurantId, ownerUserId);

        List<MenuItem> items = menuItemRepository.findByRestaurantId(restaurantId);
        Map<Long, Category> categories = categoryLookup.byIds(items.stream().map(MenuItem::getCategoryId).toList());

        return items.stream()
                .sorted(Comparator.comparing((MenuItem item) -> categories.get(item.getCategoryId()).getDisplayOrder())
                        .thenComparing(MenuItem::getName, String.CASE_INSENSITIVE_ORDER))
                .map(item -> menuItemMapper.toResponse(item, categories.get(item.getCategoryId())))
                .toList();
    }

    public MenuItemResponse getMenuItem(Long ownerUserId, Long restaurantId, Long itemId) {
        restaurantAccessGuard.requireOwnedRestaurant(restaurantId, ownerUserId);
        return toResponse(findOwnedItem(restaurantId, itemId));
    }

    public MenuItemResponse createMenuItem(Long ownerUserId, Long restaurantId, MenuItemRequest request) {
        restaurantAccessGuard.requireEditableOwnedRestaurant(restaurantId, ownerUserId);

        return transactionTemplate.execute(transaction ->
                menuItemEditor.create(restaurantId, request, MenuItemStatus.ACTIVE));
    }

    public MenuItemResponse updateMenuItem(Long ownerUserId, Long restaurantId, Long itemId, MenuItemRequest request) {
        if (request.version() == null) {
            throw new InvalidRequestException("version is required when updating a menu item");
        }
        restaurantAccessGuard.requireEditableOwnedRestaurant(restaurantId, ownerUserId);

        return transactionTemplate.execute(transaction ->
                menuItemEditor.update(findOwnedItem(restaurantId, itemId), request));
    }

    public MenuItemResponse updatePrice(Long ownerUserId, Long restaurantId, Long itemId, PriceUpdateRequest request) {
        restaurantAccessGuard.requireEditableOwnedRestaurant(restaurantId, ownerUserId);

        return transactionTemplate.execute(transaction -> {
            MenuItem item = findOwnedItem(restaurantId, itemId);
            menuItemEditor.ensureSameVersion(item, request.version());

            BigDecimal oldPrice = item.getPrice();
            item.setPrice(request.price());
            log.info("Menu item price changed. restaurantId={} menuItemId={} oldPrice={} newPrice={}",
                    restaurantId, itemId, oldPrice, request.price());
            return menuItemEditor.saveAndPublish(item, MenuItemChange.PRICE_CHANGED);
        });
    }

    /** Sold out / back in stock. Quick toggle, so no version check. */
    public MenuItemResponse updateAvailability(Long ownerUserId, Long restaurantId, Long itemId, boolean available) {
        restaurantAccessGuard.requireEditableOwnedRestaurant(restaurantId, ownerUserId);

        return transactionTemplate.execute(transaction -> {
            MenuItem item = findOwnedItem(restaurantId, itemId);
            item.setAvailable(available);
            return menuItemEditor.saveAndPublish(item, MenuItemChange.AVAILABILITY_CHANGED);
        });
    }

    public MenuItemResponse enableMenuItem(Long ownerUserId, Long restaurantId, Long itemId) {
        return changeStatusByOwner(ownerUserId, restaurantId, itemId, MenuItemStatus.ACTIVE);
    }

    public MenuItemResponse disableMenuItem(Long ownerUserId, Long restaurantId, Long itemId) {
        return changeStatusByOwner(ownerUserId, restaurantId, itemId, MenuItemStatus.DISABLED);
    }

    /** Owners switch between ACTIVE and DISABLED. A BLOCKED item can only be released by a platform admin. */
    private MenuItemResponse changeStatusByOwner(Long ownerUserId, Long restaurantId, Long itemId,
                                                 MenuItemStatus newStatus) {
        restaurantAccessGuard.requireEditableOwnedRestaurant(restaurantId, ownerUserId);

        return transactionTemplate.execute(transaction -> {
            MenuItem item = findOwnedItem(restaurantId, itemId);

            if (item.getStatus() == MenuItemStatus.BLOCKED) {
                throw new BusinessRuleException("This item was blocked by a platform admin. Contact platform support.");
            }
            if (item.getStatus() == newStatus) {
                throw new ConflictException("Menu item " + itemId + " is already " + newStatus);
            }

            item.setStatus(newStatus);
            log.info("Menu item status changed by owner. menuItemId={} newStatus={}", itemId, newStatus);
            return menuItemEditor.saveAndPublish(item, MenuItemChange.STATUS_CHANGED);
        });
    }

    private MenuItem findOwnedItem(Long restaurantId, Long itemId) {
        return menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + itemId));
    }

    private MenuItemResponse toResponse(MenuItem item) {
        Category category = categoryLookup.byIds(List.of(item.getCategoryId())).get(item.getCategoryId());
        return menuItemMapper.toResponse(item, category);
    }
}
