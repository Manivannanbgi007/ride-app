package com.fooddelivery.catalogservice.mapper;

import com.fooddelivery.catalogservice.dto.request.CategoryRequest;
import com.fooddelivery.catalogservice.dto.response.CategoryResponse;
import com.fooddelivery.catalogservice.dto.response.PublicCategoryResponse;
import com.fooddelivery.catalogservice.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public void applyRequest(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(blankToNull(request.description()));
        category.setImageUrl(blankToNull(request.imageUrl()));
    }

    public CategoryResponse toResponse(Category category, long menuItemCount) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl(),
                category.getDisplayOrder(),
                category.getStatus(),
                menuItemCount,
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getVersion());
    }

    public PublicCategoryResponse toPublicResponse(Category category) {
        return new PublicCategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
