package com.fooddelivery.catalogservice.controller;

import java.util.List;

import com.fooddelivery.catalogservice.dto.response.MenuResponse;
import com.fooddelivery.catalogservice.dto.response.PublicCategoryResponse;
import com.fooddelivery.catalogservice.service.CategoryService;
import com.fooddelivery.catalogservice.service.PublicMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Customer-facing catalog. No identity headers needed. */
@Tag(name = "Catalog", description = "Food categories and restaurant menus for customers")
@RestController
@RequiredArgsConstructor
public class PublicCatalogController {

    private final CategoryService categoryService;
    private final PublicMenuService publicMenuService;

    @Operation(summary = "Active food categories, in display order")
    @GetMapping("/api/v1/categories")
    public List<PublicCategoryResponse> getCategories() {
        return categoryService.getActiveCategories();
    }

    @Operation(summary = "Menu of an active restaurant, grouped by category",
            description = "Routed here by the API Gateway even though the path starts with /api/v1/restaurants")
    @GetMapping("/api/v1/restaurants/{restaurantId}/menu")
    public MenuResponse getMenu(@PathVariable Long restaurantId) {
        return publicMenuService.getMenu(restaurantId);
    }
}
