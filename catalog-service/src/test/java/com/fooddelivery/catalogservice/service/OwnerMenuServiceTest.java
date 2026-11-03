package com.fooddelivery.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.fooddelivery.catalogservice.client.RestaurantSummary;
import com.fooddelivery.catalogservice.config.AppProperties;
import com.fooddelivery.catalogservice.dto.request.MenuItemRequest;
import com.fooddelivery.catalogservice.dto.request.PriceUpdateRequest;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.CategoryStatus;
import com.fooddelivery.catalogservice.entity.FoodType;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.exception.RemoteServiceException;
import com.fooddelivery.catalogservice.exception.RestaurantNotFoundException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.MenuItemChange;
import com.fooddelivery.catalogservice.mapper.MenuItemMapper;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OwnerMenuServiceTest {

    private static final Long OWNER_ID = 5L;
    private static final Long RESTAURANT_ID = 10L;
    private static final Long BIRYANI = 3L;

    @Mock
    private RestaurantDirectory restaurantDirectory;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CatalogEventPublisher catalogEventPublisher;

    private OwnerMenuService ownerMenuService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(null, new AppProperties.Menu(2));
        // Unit test: the transaction manager is a stub, TransactionTemplate just runs the callback.
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        ownerMenuService = new OwnerMenuService(new RestaurantAccessGuard(restaurantDirectory), menuItemRepository,
                new CategoryLookup(categoryRepository), new MenuItemMapper(), transactionTemplate,
                new MenuItemEditor(menuItemRepository, new CategoryLookup(categoryRepository),
                        new MenuItemMapper(), appProperties, catalogEventPublisher));
    }

    @Test
    void ownerAddsItemThatIsListedAndAvailable() {
        givenRestaurant("ACTIVE", OWNER_ID);
        givenCategory(BIRYANI, "Biryani", CategoryStatus.ACTIVE);
        when(menuItemRepository.saveAndFlush(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItemResponse item = ownerMenuService.createMenuItem(OWNER_ID, RESTAURANT_ID,
                request("Chicken Biryani", BIRYANI, null));

        assertThat(item.status()).isEqualTo(MenuItemStatus.ACTIVE);
        assertThat(item.available()).isTrue();
        assertThat(item.categoryName()).isEqualTo("Biryani");
        assertThat(item.restaurantId()).isEqualTo(RESTAURANT_ID);
        // Announced for search, after the flush (so with its version)
        verify(catalogEventPublisher).menuItemChanged(any(MenuItem.class), eq(MenuItemChange.CREATED));
    }

    @Test
    void anotherOwnersRestaurantLooksLikeItDoesNotExist() {
        givenRestaurant("ACTIVE", 999L);

        var request = request("Chicken Biryani", BIRYANI, null);
        assertThatThrownBy(() -> ownerMenuService.createMenuItem(OWNER_ID, RESTAURANT_ID, request))
                .isInstanceOf(RestaurantNotFoundException.class);
        verify(menuItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void menuOfSuspendedRestaurantCannotBeChanged() {
        givenRestaurant("SUSPENDED", OWNER_ID);

        var request = request("Chicken Biryani", BIRYANI, null);
        assertThatThrownBy(() -> ownerMenuService.createMenuItem(OWNER_ID, RESTAURANT_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void inactiveCategoryCannotBeUsed() {
        givenRestaurant("ACTIVE", OWNER_ID);
        givenCategory(BIRYANI, "Biryani", CategoryStatus.INACTIVE);

        var request = request("Chicken Biryani", BIRYANI, null);
        assertThatThrownBy(() -> ownerMenuService.createMenuItem(OWNER_ID, RESTAURANT_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void dishNameMustBeUniqueWithinTheRestaurant() {
        givenRestaurant("ACTIVE", OWNER_ID);
        when(menuItemRepository.existsByRestaurantIdAndName(RESTAURANT_ID, "Chicken Biryani")).thenReturn(true);

        var request = request(" Chicken Biryani ", BIRYANI, null);
        assertThatThrownBy(() -> ownerMenuService.createMenuItem(OWNER_ID, RESTAURANT_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void menuSizeIsLimited() {
        givenRestaurant("ACTIVE", OWNER_ID);
        when(menuItemRepository.countByRestaurantId(RESTAURANT_ID)).thenReturn(2L);

        var request = request("Third Dish", BIRYANI, null);
        assertThatThrownBy(() -> ownerMenuService.createMenuItem(OWNER_ID, RESTAURANT_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most 2");
    }

    @Test
    void ownerCannotReEnableAnItemBlockedByAdmin() {
        givenRestaurant("ACTIVE", OWNER_ID);
        givenItem(20L, MenuItemStatus.BLOCKED, 0L);

        assertThatThrownBy(() -> ownerMenuService.enableMenuItem(OWNER_ID, RESTAURANT_ID, 20L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("blocked by a platform admin");
    }

    @Test
    void priceChangeOnAnOutdatedVersionIsAConflict() {
        givenRestaurant("ACTIVE", OWNER_ID);
        givenItem(20L, MenuItemStatus.ACTIVE, 4L);

        var priceUpdateRequest = new PriceUpdateRequest(new BigDecimal("349.00"), 3L);
        assertThatThrownBy(() -> ownerMenuService.updatePrice(OWNER_ID, RESTAURANT_ID, 20L, priceUpdateRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("current version 4");
    }

    @Test
    void ownershipIsNeverGuessedWhenRestaurantServiceIsDown() {
        when(restaurantDirectory.findRestaurant(RESTAURANT_ID))
                .thenThrow(new RemoteServiceException("restaurant-service", new RuntimeException("timeout")));

        var request = request("Chicken Biryani", BIRYANI, null);
        assertThatThrownBy(() -> ownerMenuService.createMenuItem(OWNER_ID, RESTAURANT_ID, request))
                .isInstanceOf(RemoteServiceException.class);
        verify(menuItemRepository, never()).saveAndFlush(any());
    }

    private void givenRestaurant(String status, Long ownerUserId) {
        when(restaurantDirectory.findRestaurant(RESTAURANT_ID))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT_ID, ownerUserId, "Spice Hub", status, 0L)));
    }

    private void givenCategory(Long id, String name, CategoryStatus status) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setStatus(status);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
    }

    private void givenItem(Long id, MenuItemStatus status, Long version) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setRestaurantId(RESTAURANT_ID);
        item.setCategoryId(BIRYANI);
        item.setName("Chicken Biryani");
        item.setPrice(new BigDecimal("299.00"));
        item.setFoodType(FoodType.NON_VEG);
        item.setStatus(status);
        item.setVersion(version);
        when(menuItemRepository.findByIdAndRestaurantId(id, RESTAURANT_ID)).thenReturn(Optional.of(item));
    }

    private static MenuItemRequest request(String name, Long categoryId, Long version) {
        return new MenuItemRequest(name, "Slow-cooked dum biryani", new BigDecimal("299.00"), categoryId,
                FoodType.NON_VEG, null, null, null, version);
    }
}
