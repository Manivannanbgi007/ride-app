package com.fooddelivery.catalogservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fooddelivery.catalogservice.dto.request.AdminMenuItemSearchCriteria;
import com.fooddelivery.catalogservice.dto.response.AuditLogResponse;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.dto.response.PageResponse;
import com.fooddelivery.catalogservice.entity.AuditAction;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.entity.RestaurantSnapshot;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.exception.InvalidRequestException;
import com.fooddelivery.catalogservice.exception.ResourceNotFoundException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.MenuItemChange;
import com.fooddelivery.catalogservice.mapper.MenuItemMapper;
import com.fooddelivery.catalogservice.repository.CatalogSpecifications;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.repository.RestaurantSnapshotRepository;
import com.fooddelivery.catalogservice.validation.SortValidator;
import com.fooddelivery.catalogservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Platform admins monitoring menu items across all restaurants. Every change is audited. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMenuItemService {

    private static final List<String> SORTABLE_FIELDS = List.of("name", "price", "createdAt", "updatedAt", "restaurantId");

    private final MenuItemRepository menuItemRepository;
    private final CategoryLookup categoryLookup;
    private final MenuItemMapper menuItemMapper;
    private final RestaurantSnapshotRepository restaurantSnapshotRepository;
    private final AdminAuditService adminAuditService;
    private final CatalogEventPublisher catalogEventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<MenuItemResponse> searchMenuItems(AdminMenuItemSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new InvalidRequestException("minPrice must not be greater than maxPrice");
        }

        List<Specification<MenuItem>> conditions = new ArrayList<>();
        if (StringUtils.hasText(criteria.search())) {
            conditions.add(CatalogSpecifications.itemNameContains(criteria.search()));
        }
        if (criteria.restaurantId() != null) {
            conditions.add(CatalogSpecifications.itemOfRestaurant(criteria.restaurantId()));
        }
        if (criteria.categoryId() != null) {
            conditions.add(CatalogSpecifications.itemInCategory(criteria.categoryId()));
        }
        if (criteria.status() != null) {
            conditions.add(CatalogSpecifications.itemHasStatus(criteria.status()));
        }
        if (criteria.available() != null) {
            conditions.add(CatalogSpecifications.itemIsAvailable(criteria.available()));
        }
        if (criteria.minPrice() != null) {
            conditions.add(CatalogSpecifications.itemPriceAtLeast(criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            conditions.add(CatalogSpecifications.itemPriceAtMost(criteria.maxPrice()));
        }

        Page<MenuItem> items = menuItemRepository.findAll(Specification.allOf(conditions), pageable);
        Map<Long, Category> categories = categoryLookup.byIds(items.map(MenuItem::getCategoryId).toSet());

        // The restaurants' names come from the local copy catalog-service keeps (one query for the whole page)
        Map<Long, String> restaurantNames = restaurantSnapshotRepository
                .findAllById(items.map(MenuItem::getRestaurantId).toSet()).stream()
                .collect(Collectors.toMap(RestaurantSnapshot::getRestaurantId, RestaurantSnapshot::getName));

        return PageResponse.from(items.map(item -> menuItemMapper.toResponse(item,
                categories.get(item.getCategoryId()), restaurantNames.get(item.getRestaurantId()))));
    }

    @Transactional(readOnly = true)
    public MenuItemResponse getMenuItem(Long itemId) {
        return toResponse(findItem(itemId));
    }

    /** Lists the item again (from DISABLED or BLOCKED). */
    @Transactional
    public MenuItemResponse activateMenuItem(Long itemId, String reason, CallerInfo admin) {
        return changeStatus(itemId, MenuItemStatus.ACTIVE, AuditAction.MENU_ITEM_ACTIVATED_BY_ADMIN, reason, admin);
    }

    /** Hides the item; the owner cannot re-enable it until an admin activates it again. */
    @Transactional
    public MenuItemResponse blockMenuItem(Long itemId, String reason, CallerInfo admin) {
        return changeStatus(itemId, MenuItemStatus.BLOCKED, AuditAction.MENU_ITEM_BLOCKED_BY_ADMIN, reason, admin);
    }

    @Transactional
    public MenuItemResponse changeCategory(Long itemId, Long categoryId, String reason, CallerInfo admin) {
        MenuItem item = findItem(itemId);
        if (item.getCategoryId().equals(categoryId)) {
            throw new ConflictException("Menu item " + itemId + " is already in category " + categoryId);
        }
        Category newCategory = categoryLookup.requireAssignable(categoryId);

        Long oldCategoryId = item.getCategoryId();
        item.setCategoryId(categoryId);
        menuItemRepository.flush();
        catalogEventPublisher.menuItemChanged(item, MenuItemChange.CATEGORY_CHANGED);

        adminAuditService.recordAction(admin, AuditAction.MENU_ITEM_CATEGORY_CHANGED_BY_ADMIN,
                AdminAuditService.ENTITY_TYPE_MENU_ITEM, itemId,
                "categoryId=" + oldCategoryId, "categoryId=" + categoryId + " (" + newCategory.getName() + ")", reason);
        return menuItemMapper.toResponse(item, newCategory);
    }

    /**
     * Permanently removes an invalid item. Business rule: only items that are already hidden
     * (DISABLED or BLOCKED) can be removed, so nothing disappears from a live menu by accident.
     * Past orders are not affected: orders keep their own copy of name and price.
     */
    @Transactional
    public void removeMenuItem(Long itemId, String reason, CallerInfo admin) {
        MenuItem item = findItem(itemId);
        if (item.getStatus() == MenuItemStatus.ACTIVE) {
            throw new ConflictException("Menu item " + itemId + " is ACTIVE. Block it first, then remove it.");
        }

        // The last state goes out first: search-service removes the item from its index
        catalogEventPublisher.menuItemChanged(item, MenuItemChange.REMOVED);
        menuItemRepository.delete(item);
        adminAuditService.recordAction(admin, AuditAction.MENU_ITEM_REMOVED_BY_ADMIN, AdminAuditService.ENTITY_TYPE_MENU_ITEM,
                itemId, "restaurantId=" + item.getRestaurantId() + "; name=" + item.getName() + "; price=" + item.getPrice(),
                null, reason);
        log.info("Menu item removed by admin. menuItemId={} restaurantId={} adminUserId={}",
                itemId, item.getRestaurantId(), admin.userId());
    }

    /** Audit history survives removal, so this does not require the item to still exist. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getMenuItemAuditLogs(Long itemId, Pageable pageable) {
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_MENU_ITEM, itemId, pageable);
    }

    private MenuItemResponse changeStatus(Long itemId, MenuItemStatus newStatus, AuditAction action,
                                          String reason, CallerInfo admin) {
        MenuItem item = findItem(itemId);
        MenuItemStatus oldStatus = item.getStatus();
        if (oldStatus == newStatus) {
            throw new ConflictException("Menu item " + itemId + " is already " + newStatus);
        }

        item.setStatus(newStatus);
        menuItemRepository.flush();
        catalogEventPublisher.menuItemChanged(item, MenuItemChange.STATUS_CHANGED);

        adminAuditService.recordAction(admin, action, AdminAuditService.ENTITY_TYPE_MENU_ITEM, itemId,
                oldStatus.name(), newStatus.name(), reason);
        log.info("Menu item status changed by admin. menuItemId={} oldStatus={} newStatus={} adminUserId={}",
                itemId, oldStatus, newStatus, admin.userId());
        return toResponse(item);
    }

    private MenuItem findItem(Long itemId) {
        return menuItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + itemId));
    }

    private MenuItemResponse toResponse(MenuItem item) {
        return menuItemMapper.toResponse(item, categoryLookup.byIds(List.of(item.getCategoryId())).get(item.getCategoryId()));
    }
}
