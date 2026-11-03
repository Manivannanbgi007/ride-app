package com.fooddelivery.catalogservice.controller;

import java.net.URI;
import java.util.List;

import com.fooddelivery.catalogservice.dto.request.AvailabilityRequest;
import com.fooddelivery.catalogservice.dto.request.MenuItemRequest;
import com.fooddelivery.catalogservice.dto.request.PriceUpdateRequest;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.service.OwnerMenuService;
import com.fooddelivery.catalogservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restaurant owners (RESTAURANT_ADMIN) managing their menu. Enforced by RoleAccessInterceptor. */
@Tag(name = "My menu", description = "Restaurant owners: menu items, prices and availability")
@RestController
@RequestMapping("/api/v1/restaurants/mine/{restaurantId}/menu-items")
@RequiredArgsConstructor
public class OwnerMenuController {

    private final OwnerMenuService ownerMenuService;

    @Operation(summary = "All menu items of my restaurant (every status)")
    @GetMapping
    public List<MenuItemResponse> getMenuItems(@PathVariable Long restaurantId, CallerInfo caller) {
        return ownerMenuService.getMenuItems(caller.userId(), restaurantId);
    }

    @Operation(summary = "Add a menu item (listed and available right away)")
    @PostMapping
    public ResponseEntity<MenuItemResponse> createMenuItem(@PathVariable Long restaurantId,
                                                           @Valid @RequestBody MenuItemRequest request,
                                                           CallerInfo caller) {
        MenuItemResponse item = ownerMenuService.createMenuItem(caller.userId(), restaurantId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/restaurants/mine/" + restaurantId + "/menu-items/" + item.id()))
                .body(item);
    }

    @Operation(summary = "One menu item")
    @GetMapping("/{itemId}")
    public MenuItemResponse getMenuItem(@PathVariable Long restaurantId, @PathVariable Long itemId, CallerInfo caller) {
        return ownerMenuService.getMenuItem(caller.userId(), restaurantId, itemId);
    }

    @Operation(summary = "Update a menu item (send the version you last read)")
    @PutMapping("/{itemId}")
    public MenuItemResponse updateMenuItem(@PathVariable Long restaurantId, @PathVariable Long itemId,
                                           @Valid @RequestBody MenuItemRequest request, CallerInfo caller) {
        return ownerMenuService.updateMenuItem(caller.userId(), restaurantId, itemId, request);
    }

    @Operation(summary = "Change only the price (send the version you last read)")
    @PutMapping("/{itemId}/price")
    public MenuItemResponse updatePrice(@PathVariable Long restaurantId, @PathVariable Long itemId,
                                        @Valid @RequestBody PriceUpdateRequest request, CallerInfo caller) {
        return ownerMenuService.updatePrice(caller.userId(), restaurantId, itemId, request);
    }

    @Operation(summary = "Mark as sold out (available=false) or back in stock")
    @PutMapping("/{itemId}/availability")
    public MenuItemResponse updateAvailability(@PathVariable Long restaurantId, @PathVariable Long itemId,
                                               @Valid @RequestBody AvailabilityRequest request, CallerInfo caller) {
        return ownerMenuService.updateAvailability(caller.userId(), restaurantId, itemId, request.available());
    }

    @Operation(summary = "Show the item on the menu again")
    @PutMapping("/{itemId}/enable")
    public MenuItemResponse enableMenuItem(@PathVariable Long restaurantId, @PathVariable Long itemId, CallerInfo caller) {
        return ownerMenuService.enableMenuItem(caller.userId(), restaurantId, itemId);
    }

    @Operation(summary = "Hide the item from the menu")
    @PutMapping("/{itemId}/disable")
    public MenuItemResponse disableMenuItem(@PathVariable Long restaurantId, @PathVariable Long itemId, CallerInfo caller) {
        return ownerMenuService.disableMenuItem(caller.userId(), restaurantId, itemId);
    }
}
