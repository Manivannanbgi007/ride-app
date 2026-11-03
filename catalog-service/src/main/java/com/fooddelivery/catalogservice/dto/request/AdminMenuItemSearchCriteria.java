package com.fooddelivery.catalogservice.dto.request;

import java.math.BigDecimal;

import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/** Optional filters for the admin menu item list. */
public record AdminMenuItemSearchCriteria(

        @Parameter(description = "Item name contains (case-insensitive)")
        @Size(max = 120)
        String search,

        @Parameter(description = "Only items of this restaurant")
        Long restaurantId,

        @Parameter(description = "Only items in this category")
        Long categoryId,

        @Parameter(description = "ACTIVE, DISABLED (by owner) or BLOCKED (by admin)")
        MenuItemStatus status,

        @Parameter(description = "true = in stock, false = sold out")
        Boolean available,

        @Parameter(description = "Price review: minimum price")
        @DecimalMin("0.00")
        BigDecimal minPrice,

        @Parameter(description = "Price review: maximum price")
        @DecimalMin("0.00")
        BigDecimal maxPrice
) {
}
