package com.fooddelivery.catalogservice.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fooddelivery.catalogservice.dto.response.InternalMenuItemResponse;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Menu item data for other microservices, whatever the item's status. */
@Service
@RequiredArgsConstructor
public class InternalMenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;

    /** Unknown ids are simply left out; the caller decides what a missing item means. */
    @Transactional(readOnly = true)
    public List<InternalMenuItemResponse> getMenuItems(List<Long> ids) {
        List<MenuItem> items = menuItemRepository.findAllById(ids);
        // One query for all the categories, not one per item
        Map<Long, String> categoryNames = categoryRepository
                .findAllById(items.stream().map(MenuItem::getCategoryId).distinct().toList()).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        return items.stream()
                .map(item -> new InternalMenuItemResponse(item.getId(), item.getRestaurantId(), item.getName(),
                        item.getPrice(), item.getFoodType(), item.getStatus(), item.isAvailable(),
                        item.getImageUrl(), categoryNames.get(item.getCategoryId())))
                .toList();
    }
}
