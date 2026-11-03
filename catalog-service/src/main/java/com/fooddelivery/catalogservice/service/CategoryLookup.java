package com.fooddelivery.catalogservice.service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.CategoryStatus;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Category lookups shared by the menu services. */
@Component
@RequiredArgsConstructor
public class CategoryLookup {

    private final CategoryRepository categoryRepository;

    /** Loads several categories in one query, keyed by id. */
    public Map<Long, Category> byIds(Collection<Long> categoryIds) {
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /** A menu item can only be put into an existing, ACTIVE category. */
    public Category requireAssignable(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Category " + categoryId + " does not exist"));
        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new BusinessRuleException("Category '" + category.getName() + "' is inactive and cannot be used");
        }
        return category;
    }
}
