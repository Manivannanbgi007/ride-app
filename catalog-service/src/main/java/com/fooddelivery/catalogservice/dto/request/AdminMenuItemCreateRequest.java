package com.fooddelivery.catalogservice.dto.request;

import java.math.BigDecimal;

import com.fooddelivery.catalogservice.entity.FoodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/** A platform admin adding a menu item to any restaurant: the fields of {@link MenuItemRequest} plus the restaurant. */
public record AdminMenuItemCreateRequest(

        @Schema(description = "The restaurant that gets the item")
        @NotNull
        Long restaurantId,

        @Schema(example = "Chicken Dum Biryani")
        @NotBlank @Size(max = 120)
        String name,

        @Size(max = 500)
        String description,

        @Schema(description = "Price in INR", example = "299.00")
        @NotNull @DecimalMin("1.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2)
        BigDecimal price,

        @Schema(description = "Global food category id (see GET /api/v1/categories)")
        @NotNull
        Long categoryId,

        @NotNull
        FoodType foodType,

        @URL @Size(max = 500)
        String imageUrl,

        @Schema(description = "Optional free text, e.g. rice, chicken, spices")
        @Size(max = 1000)
        String ingredients,

        @Schema(description = "Optional free text, e.g. Calories 450 kcal, Protein 20 g")
        @Size(max = 1000)
        String nutritionInfo
) {

    /** The part that follows the owner's rules; a new item has no version yet. */
    public MenuItemRequest toMenuItemRequest() {
        return new MenuItemRequest(name, description, price, categoryId, foodType, imageUrl, ingredients,
                nutritionInfo, null);
    }
}
