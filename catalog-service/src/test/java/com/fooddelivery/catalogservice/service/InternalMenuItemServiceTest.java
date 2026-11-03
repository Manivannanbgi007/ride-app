package com.fooddelivery.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fooddelivery.catalogservice.dto.response.InternalMenuItemResponse;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;

/** The menu item data other services read: including the photo and the category name. */
class InternalMenuItemServiceTest {

    private final MenuItemRepository menuItems = mock(MenuItemRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final InternalMenuItemService service = new InternalMenuItemService(menuItems, categories);

    private static MenuItem item(long id, long categoryId, String imageUrl) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setRestaurantId(3L);
        item.setCategoryId(categoryId);
        item.setName("Dish " + id);
        item.setImageUrl(imageUrl);
        return item;
    }

    @Test
    void itemsCarryTheirPhotoAndCategoryNameFromOneCategoryQuery() {
        Category mains = new Category();
        mains.setId(7L);
        mains.setName("Main course");
        MenuItem first = item(1, 7, "https://img/1.jpg");
        MenuItem second = item(2, 7, null);
        when(menuItems.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(categories.findAllById(List.of(7L))).thenReturn(List.of(mains));

        List<InternalMenuItemResponse> found = service.getMenuItems(List.of(1L, 2L));

        assertThat(found).extracting(InternalMenuItemResponse::imageUrl).containsExactly("https://img/1.jpg", null);
        assertThat(found).extracting(InternalMenuItemResponse::categoryName).containsOnly("Main course");
        verify(categories).findAllById(List.of(7L));
    }
}
