package com.fooddelivery.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.catalogservice.dto.request.AdminMenuItemSearchCriteria;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.entity.AuditAction;
import com.fooddelivery.catalogservice.entity.FoodType;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.entity.RestaurantSnapshot;
import com.fooddelivery.catalogservice.entity.Role;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.exception.InvalidRequestException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.MenuItemChange;
import com.fooddelivery.catalogservice.mapper.MenuItemMapper;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.repository.RestaurantSnapshotRepository;
import com.fooddelivery.catalogservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminMenuItemServiceTest {

    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "10.0.0.1", "req-1");

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RestaurantSnapshotRepository restaurantSnapshotRepository;

    @Mock
    private AdminAuditService adminAuditService;

    @Mock
    private CatalogEventPublisher catalogEventPublisher;

    private AdminMenuItemService adminMenuItemService;

    @BeforeEach
    void setUp() {
        adminMenuItemService = new AdminMenuItemService(menuItemRepository, new CategoryLookup(categoryRepository),
                new MenuItemMapper(), restaurantSnapshotRepository, adminAuditService, catalogEventPublisher);
    }

    @Test
    void blockingAnItemIsAudited() {
        givenItem(20L, MenuItemStatus.ACTIVE);

        MenuItemResponse response = adminMenuItemService.blockMenuItem(20L, "Misleading description", ADMIN);

        assertThat(response.status()).isEqualTo(MenuItemStatus.BLOCKED);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.MENU_ITEM_BLOCKED_BY_ADMIN, "MENU_ITEM", 20L,
                "ACTIVE", "BLOCKED", "Misleading description");
    }

    @Test
    void liveItemsCannotBeRemoved() {
        MenuItem item = givenItem(20L, MenuItemStatus.ACTIVE);

        assertThatThrownBy(() -> adminMenuItemService.removeMenuItem(20L, "Invalid", ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Block it first");
        verify(menuItemRepository, never()).delete(item);
    }

    @Test
    void blockedItemIsRemovedAndTheAuditKeepsWhatItWas() {
        MenuItem item = givenItem(20L, MenuItemStatus.BLOCKED);

        adminMenuItemService.removeMenuItem(20L, "Duplicate listing", ADMIN);

        // search-service hears about it before the row is gone
        InOrder inOrder = inOrder(catalogEventPublisher, menuItemRepository);
        inOrder.verify(catalogEventPublisher).menuItemChanged(item, MenuItemChange.REMOVED);
        inOrder.verify(menuItemRepository).delete(item);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.MENU_ITEM_REMOVED_BY_ADMIN, "MENU_ITEM", 20L,
                "restaurantId=10; name=Chicken Biryani; price=299.00", null, "Duplicate listing");
    }

    @Test
    void priceRangeMustBeValid() {
        AdminMenuItemSearchCriteria criteria = new AdminMenuItemSearchCriteria(null, null, null, null, null,
                new BigDecimal("500"), new BigDecimal("100"));

        var page = PageRequest.of(0, 20);
        assertThatThrownBy(() -> adminMenuItemService.searchMenuItems(criteria, page))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void theListNamesTheRestaurantOfEachItemFromTheLocalCopy() {
        MenuItem item = new MenuItem();
        item.setId(20L);
        item.setRestaurantId(10L);
        item.setCategoryId(3L);
        item.setName("Chicken Biryani");
        item.setPrice(new BigDecimal("299.00"));
        item.setFoodType(FoodType.NON_VEG);
        item.setStatus(MenuItemStatus.ACTIVE);
        when(menuItemRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<MenuItem>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item)));
        when(restaurantSnapshotRepository.findAllById(Set.of(10L))).thenReturn(List.of(
                new RestaurantSnapshot(10L, 5L, "Spice Hub", "ACTIVE", 1L, Instant.EPOCH)));

        var page = adminMenuItemService.searchMenuItems(
                new AdminMenuItemSearchCriteria(null, null, null, null, null, null, null), PageRequest.of(0, 10));

        assertThat(page.content()).extracting(MenuItemResponse::restaurantName).containsExactly("Spice Hub");
    }

    private MenuItem givenItem(Long id, MenuItemStatus status) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setRestaurantId(10L);
        item.setCategoryId(3L);
        item.setName("Chicken Biryani");
        item.setPrice(new BigDecimal("299.00"));
        item.setFoodType(FoodType.NON_VEG);
        item.setStatus(status);
        item.setVersion(0L);
        when(menuItemRepository.findById(id)).thenReturn(Optional.of(item));
        return item;
    }
}
