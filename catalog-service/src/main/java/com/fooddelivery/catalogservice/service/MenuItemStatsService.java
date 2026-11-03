package com.fooddelivery.catalogservice.service;

import java.util.List;

import com.fooddelivery.catalogservice.dto.response.CategoryStatsResponse;
import com.fooddelivery.catalogservice.dto.response.MenuItemStatsResponse;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.repository.CategoryItemStats;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.repository.MenuItemTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Menu item numbers for the admin dashboard, counted in the database. */
@Service
@RequiredArgsConstructor
public class MenuItemStatsService {

    /** Orderable = listed (this status) and in stock; the category query takes the status from here too. */
    private static final MenuItemStatus ORDERABLE_STATUS = MenuItemStatus.ACTIVE;

    private final MenuItemRepository menuItemRepository;

    @Transactional(readOnly = true)
    public MenuItemStatsResponse getStats() {
        long total = 0;
        long active = 0;
        long orderable = 0;
        for (MenuItemTotals totals : menuItemRepository.countPerStatusAndAvailability()) {
            total += totals.getTotal();
            if (totals.getStatus() == ORDERABLE_STATUS) {
                active += totals.getTotal();
                if (totals.isAvailable()) {
                    orderable += totals.getTotal();
                }
            }
        }
        return new MenuItemStatsResponse(total, active, total - active, orderable, active - orderable);
    }

    /** The categories with the most menu items and how many of them are orderable. */
    @Transactional(readOnly = true)
    public List<CategoryStatsResponse> getCategoryStats(int limit) {
        return menuItemRepository.findCategoryStats(ORDERABLE_STATUS, PageRequest.of(0, limit)).stream()
                .map(MenuItemStatsService::toResponse)
                .toList();
    }

    private static CategoryStatsResponse toResponse(CategoryItemStats stats) {
        int percent = (int) Math.round(stats.getOrderableItems() * 100.0 / stats.getTotalItems());
        return new CategoryStatsResponse(stats.getCategoryId(), stats.getCategoryName(), stats.getTotalItems(),
                stats.getOrderableItems(), percent);
    }
}
