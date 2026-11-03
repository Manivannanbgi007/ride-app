package com.fooddelivery.catalogservice.controller;

import java.net.URI;
import java.util.List;

import com.fooddelivery.catalogservice.dto.request.AdminMenuItemCreateRequest;
import com.fooddelivery.catalogservice.dto.request.AdminMenuItemSearchCriteria;
import com.fooddelivery.catalogservice.dto.request.ChangeCategoryRequest;
import com.fooddelivery.catalogservice.dto.request.MenuItemRequest;
import com.fooddelivery.catalogservice.dto.request.StatusChangeRequest;
import com.fooddelivery.catalogservice.dto.response.AuditLogResponse;
import com.fooddelivery.catalogservice.dto.response.CategoryStatsResponse;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.dto.response.MenuItemStatsResponse;
import com.fooddelivery.catalogservice.dto.response.PageResponse;
import com.fooddelivery.catalogservice.dto.response.RepublishResponse;
import com.fooddelivery.catalogservice.service.AdminMenuItemEditService;
import com.fooddelivery.catalogservice.service.AdminMenuItemService;
import com.fooddelivery.catalogservice.service.CatalogEventReplayService;
import com.fooddelivery.catalogservice.service.MenuItemStatsService;
import com.fooddelivery.catalogservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform admins monitoring menu items of all restaurants. Access restricted to PLATFORM_ADMIN. */
@Tag(name = "Admin - Menu items", description = "Monitor and control menu items across the platform")
@RestController
@RequestMapping("/api/v1/admin/menu-items")
@RequiredArgsConstructor
public class AdminMenuItemController {

    private final AdminMenuItemService adminMenuItemService;
    private final AdminMenuItemEditService adminMenuItemEditService;
    private final CatalogEventReplayService catalogEventReplayService;
    private final MenuItemStatsService menuItemStatsService;

    @Operation(summary = "Search menu items",
            description = "Price review: ?minPrice=1000&sort=price,desc   Availability review: ?available=false")
    @GetMapping
    public PageResponse<MenuItemResponse> searchMenuItems(
            @Valid @ParameterObject AdminMenuItemSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminMenuItemService.searchMenuItems(criteria, pageable);
    }

    @Operation(summary = "Menu item numbers right now: listed, disabled, orderable, sold out")
    @GetMapping("/stats")
    public MenuItemStatsResponse getStats() {
        return menuItemStatsService.getStats();
    }

    @Operation(summary = "Categories with the most menu items and how many are orderable (listed and in stock)")
    @GetMapping("/category-stats")
    public List<CategoryStatsResponse> getCategoryStats(
            @RequestParam(defaultValue = "3") @Min(1) @Max(12) int limit) {
        return menuItemStatsService.getCategoryStats(limit);
    }

    @Operation(summary = "Add a menu item to any restaurant (listed and available, same rules as the owner)")
    @PostMapping
    public ResponseEntity<MenuItemResponse> createMenuItem(@Valid @RequestBody AdminMenuItemCreateRequest request,
                                                           CallerInfo caller) {
        MenuItemResponse item = adminMenuItemEditService.createMenuItem(request, caller);
        return ResponseEntity.created(URI.create("/api/v1/admin/menu-items/" + item.id())).body(item);
    }

    @Operation(summary = "Edit a menu item (send the version you last read; status and availability stay)")
    @PutMapping("/{itemId}")
    public MenuItemResponse updateMenuItem(@PathVariable Long itemId, @Valid @RequestBody MenuItemRequest request,
                                           CallerInfo caller) {
        return adminMenuItemEditService.updateMenuItem(itemId, request, caller);
    }

    @Operation(summary = "Duplicate a menu item (the copy is DISABLED until someone enables it)")
    @PostMapping("/{itemId}/duplicate")
    public ResponseEntity<MenuItemResponse> duplicateMenuItem(@PathVariable Long itemId, CallerInfo caller) {
        MenuItemResponse copy = adminMenuItemEditService.duplicateMenuItem(itemId, caller);
        return ResponseEntity.created(URI.create("/api/v1/admin/menu-items/" + copy.id())).body(copy);
    }

    @Operation(summary = "One menu item")
    @GetMapping("/{itemId}")
    public MenuItemResponse getMenuItem(@PathVariable Long itemId) {
        return adminMenuItemService.getMenuItem(itemId);
    }

    @Operation(summary = "Activate (list again, also releases a BLOCKED item)")
    @PutMapping("/{itemId}/activate")
    public MenuItemResponse activateMenuItem(@PathVariable Long itemId,
                                             @Valid @RequestBody StatusChangeRequest request,
                                             CallerInfo caller) {
        return adminMenuItemService.activateMenuItem(itemId, request.reason(), caller);
    }

    @Operation(summary = "Deactivate (BLOCKED: the owner cannot re-enable it)")
    @PutMapping("/{itemId}/deactivate")
    public MenuItemResponse deactivateMenuItem(@PathVariable Long itemId,
                                               @Valid @RequestBody StatusChangeRequest request,
                                               CallerInfo caller) {
        return adminMenuItemService.blockMenuItem(itemId, request.reason(), caller);
    }

    @Operation(summary = "Move the item to another category")
    @PutMapping("/{itemId}/category")
    public MenuItemResponse changeCategory(@PathVariable Long itemId,
                                           @Valid @RequestBody ChangeCategoryRequest request,
                                           CallerInfo caller) {
        return adminMenuItemService.changeCategory(itemId, request.categoryId(), request.reason(), caller);
    }

    @Operation(summary = "Remove an invalid item (only DISABLED or BLOCKED items)")
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeMenuItem(@PathVariable Long itemId,
                                               @RequestParam(required = false) @Size(max = 500) String reason,
                                               CallerInfo caller) {
        adminMenuItemService.removeMenuItem(itemId, reason, caller);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Audit history of a menu item (newest first)")
    @GetMapping("/{itemId}/audit-logs")
    public PageResponse<AuditLogResponse> getMenuItemAuditLogs(@PathVariable Long itemId,
                                                               @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminMenuItemService.getMenuItemAuditLogs(itemId, pageable);
    }

    @Operation(summary = "Publish every menu item again as a MenuItemChanged event (audited)",
            description = "For consumers that need to catch up, for example after rebuilding the search index. "
                    + "Changes no data; consumers ignore versions they already have.")
    @PostMapping("/events/republish")
    public RepublishResponse republishEvents(CallerInfo caller) {
        return catalogEventReplayService.republishMenuItems(caller);
    }
}
