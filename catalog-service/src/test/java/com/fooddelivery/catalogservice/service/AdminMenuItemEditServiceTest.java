package com.fooddelivery.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.fooddelivery.catalogservice.client.RestaurantSummary;
import com.fooddelivery.catalogservice.config.AppProperties;
import com.fooddelivery.catalogservice.dto.request.AdminMenuItemCreateRequest;
import com.fooddelivery.catalogservice.dto.request.MenuItemRequest;
import com.fooddelivery.catalogservice.dto.response.MenuItemResponse;
import com.fooddelivery.catalogservice.entity.AuditAction;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.CategoryStatus;
import com.fooddelivery.catalogservice.entity.FoodType;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import com.fooddelivery.catalogservice.entity.Role;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.exception.InvalidRequestException;
import com.fooddelivery.catalogservice.exception.ResourceNotFoundException;
import com.fooddelivery.catalogservice.exception.RestaurantNotFoundException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.MenuItemChange;
import com.fooddelivery.catalogservice.mapper.MenuItemMapper;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AdminMenuItemEditServiceTest {

    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "10.0.0.1", "req-1");
    private static final Long RESTAURANT_ID = 10L;
    private static final Long BIRYANI = 3L;

    @Mock
    private RestaurantDirectory restaurantDirectory;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AdminAuditService adminAuditService;

    @Mock
    private CatalogEventPublisher catalogEventPublisher;

    private AdminMenuItemEditService service;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(null, new AppProperties.Menu(2));
        MenuItemEditor editor = new MenuItemEditor(menuItemRepository, new CategoryLookup(categoryRepository),
                new MenuItemMapper(), appProperties, catalogEventPublisher);
        // Unit test: the transaction manager is a stub, TransactionTemplate just runs the callback.
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new AdminMenuItemEditService(new RestaurantAccessGuard(restaurantDirectory), menuItemRepository,
                editor, adminAuditService, transactionTemplate);
    }

    @Test
    void adminCreatesAListedItemForAnyRestaurantAndItIsAudited() {
        givenRestaurant("ACTIVE");
        givenCategory(CategoryStatus.ACTIVE);
        saveReturnsItsArgument();

        MenuItemResponse item = service.createMenuItem(createRequest("Chicken Biryani"), ADMIN);

        assertThat(item.status()).isEqualTo(MenuItemStatus.ACTIVE);
        assertThat(item.available()).isTrue();
        verify(catalogEventPublisher).menuItemChanged(any(MenuItem.class), eq(MenuItemChange.CREATED));
        verify(adminAuditService).recordAction(eq(ADMIN), eq(AuditAction.MENU_ITEM_CREATED_BY_ADMIN), eq("MENU_ITEM"),
                any(), eq(null), anyString(), eq("Created for restaurant 10"));
    }

    @Test
    void unknownRestaurantIsNotFound() {
        when(restaurantDirectory.findRestaurant(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createMenuItem(createRequest("Chicken Biryani"), ADMIN))
                .isInstanceOf(RestaurantNotFoundException.class);
        verify(menuItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void suspendedRestaurantMenuCannotBeChanged() {
        givenRestaurant("SUSPENDED");

        assertThatThrownBy(() -> service.createMenuItem(createRequest("Chicken Biryani"), ADMIN))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duplicateNameIsRejectedLikeForOwners() {
        givenRestaurant("ACTIVE");
        when(menuItemRepository.existsByRestaurantIdAndName(RESTAURANT_ID, "Chicken Biryani")).thenReturn(true);

        assertThatThrownBy(() -> service.createMenuItem(createRequest("Chicken Biryani"), ADMIN))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void editKeepsStatusAndAvailabilityOfABlockedItem() {
        givenRestaurant("ACTIVE");
        MenuItem item = givenItem(20L, "Chicken Biryani", MenuItemStatus.BLOCKED, 4L);
        item.setAvailable(false);
        saveReturnsItsArgument();

        MenuItemResponse updated = service.updateMenuItem(20L, request("Mutton Biryani", 4L), ADMIN);

        assertThat(updated.name()).isEqualTo("Mutton Biryani");
        assertThat(updated.status()).isEqualTo(MenuItemStatus.BLOCKED);
        assertThat(updated.available()).isFalse();
        verify(catalogEventPublisher).menuItemChanged(item, MenuItemChange.UPDATED);
        verify(adminAuditService).recordAction(eq(ADMIN), eq(AuditAction.MENU_ITEM_UPDATED_BY_ADMIN), eq("MENU_ITEM"),
                eq(20L), anyString(), anyString(), anyString());
    }

    @Test
    void editWithAnOutdatedVersionIsRejected() {
        givenRestaurant("ACTIVE");
        givenItem(20L, "Chicken Biryani", MenuItemStatus.ACTIVE, 4L);

        assertThatThrownBy(() -> service.updateMenuItem(20L, request("Mutton Biryani", 3L), ADMIN))
                .isInstanceOf(ConflictException.class);
        verify(menuItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void editWithoutVersionIsRejected() {
        assertThatThrownBy(() -> service.updateMenuItem(20L, request("Mutton Biryani", null), ADMIN))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createStoresIngredientsAndNutritionAndBlankBecomesNull() {
        givenRestaurant("ACTIVE");
        givenCategory(CategoryStatus.ACTIVE);
        saveReturnsItsArgument();

        MenuItemResponse item = service.createMenuItem(new AdminMenuItemCreateRequest(RESTAURANT_ID, "Chicken Biryani",
                null, new BigDecimal("299.00"), BIRYANI, FoodType.NON_VEG, null, " Rice, chicken ", "   "), ADMIN);

        assertThat(item.ingredients()).isEqualTo("Rice, chicken");
        assertThat(item.nutritionInfo()).isNull();
    }

    @Test
    void updateChangesAndClearsIngredientsAndNutrition() {
        givenRestaurant("ACTIVE");
        MenuItem item = givenItem(20L, "Chicken Biryani", MenuItemStatus.ACTIVE, 4L);
        saveReturnsItsArgument();

        MenuItemResponse changed = service.updateMenuItem(20L, new MenuItemRequest("Chicken Biryani", null,
                new BigDecimal("299.00"), BIRYANI, FoodType.NON_VEG, null, "Rice", "Calories 450 kcal", 4L), ADMIN);
        assertThat(changed.ingredients()).isEqualTo("Rice");
        assertThat(changed.nutritionInfo()).isEqualTo("Calories 450 kcal");

        item.setVersion(5L);
        MenuItemResponse cleared = service.updateMenuItem(20L, request("Chicken Biryani", 5L), ADMIN);
        assertThat(cleared.ingredients()).isNull();
        assertThat(cleared.nutritionInfo()).isNull();
    }

    @Test
    void duplicateCopiesIngredientsAndNutrition() {
        givenRestaurant("ACTIVE");
        givenCategory(CategoryStatus.ACTIVE);
        MenuItem source = givenItem(20L, "Chicken Biryani", MenuItemStatus.ACTIVE, 0L);
        source.setIngredients("Rice, chicken");
        source.setNutritionInfo("Calories 450 kcal");
        saveReturnsItsArgument();

        MenuItemResponse copy = service.duplicateMenuItem(20L, ADMIN);

        assertThat(copy.ingredients()).isEqualTo("Rice, chicken");
        assertThat(copy.nutritionInfo()).isEqualTo("Calories 450 kcal");
    }

    @Test
    void duplicateIsHiddenAndNamedCopy() {
        givenRestaurant("ACTIVE");
        givenCategory(CategoryStatus.ACTIVE);
        givenItem(20L, "Chicken Biryani", MenuItemStatus.ACTIVE, 0L);
        saveReturnsItsArgument();

        MenuItemResponse copy = service.duplicateMenuItem(20L, ADMIN);

        assertThat(copy.name()).isEqualTo("Chicken Biryani (copy)");
        assertThat(copy.status()).isEqualTo(MenuItemStatus.DISABLED);
        assertThat(copy.price()).isEqualByComparingTo("299.00");
        assertThat(copy.categoryId()).isEqualTo(BIRYANI);
        verify(adminAuditService).recordAction(eq(ADMIN), eq(AuditAction.MENU_ITEM_DUPLICATED_BY_ADMIN),
                eq("MENU_ITEM"), any(), eq("sourceItemId=20"), anyString(), eq("Duplicated from menu item 20"));
    }

    @Test
    void duplicateCountsUpWhenTheCopyNameIsTaken() {
        givenRestaurant("ACTIVE");
        givenCategory(CategoryStatus.ACTIVE);
        givenItem(20L, "Chicken Biryani", MenuItemStatus.ACTIVE, 0L);
        when(menuItemRepository.existsByRestaurantIdAndName(RESTAURANT_ID, "Chicken Biryani (copy)")).thenReturn(true);
        when(menuItemRepository.existsByRestaurantIdAndName(RESTAURANT_ID, "Chicken Biryani (copy 2)")).thenReturn(true);
        saveReturnsItsArgument();

        assertThat(service.duplicateMenuItem(20L, ADMIN).name()).isEqualTo("Chicken Biryani (copy 3)");
    }

    @Test
    void duplicateOfALongNameTrimsTheOriginalPart() {
        givenRestaurant("ACTIVE");
        givenCategory(CategoryStatus.ACTIVE);
        givenItem(20L, "x".repeat(120), MenuItemStatus.ACTIVE, 0L);
        saveReturnsItsArgument();

        String name = service.duplicateMenuItem(20L, ADMIN).name();

        assertThat(name).hasSize(120).endsWith("x (copy)");
    }

    @Test
    void duplicateOfAnUnknownItemIsNotFound() {
        when(menuItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicateMenuItem(99L, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenRestaurant(String status) {
        when(restaurantDirectory.findRestaurant(RESTAURANT_ID))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT_ID, 5L, "Spice Hub", status, 0L)));
    }

    private void givenCategory(CategoryStatus status) {
        Category category = new Category();
        category.setId(BIRYANI);
        category.setName("Biryani");
        category.setStatus(status);
        when(categoryRepository.findById(BIRYANI)).thenReturn(Optional.of(category));
    }

    private MenuItem givenItem(Long id, String name, MenuItemStatus status, Long version) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setRestaurantId(RESTAURANT_ID);
        item.setCategoryId(BIRYANI);
        item.setName(name);
        item.setPrice(new BigDecimal("299.00"));
        item.setFoodType(FoodType.NON_VEG);
        item.setStatus(status);
        item.setVersion(version);
        when(menuItemRepository.findById(id)).thenReturn(Optional.of(item));
        return item;
    }

    private void saveReturnsItsArgument() {
        when(menuItemRepository.saveAndFlush(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static AdminMenuItemCreateRequest createRequest(String name) {
        return new AdminMenuItemCreateRequest(RESTAURANT_ID, name, null, new BigDecimal("299.00"), BIRYANI,
                FoodType.NON_VEG, null, null, null);
    }

    private static MenuItemRequest request(String name, Long version) {
        return new MenuItemRequest(name, null, new BigDecimal("299.00"), BIRYANI, FoodType.NON_VEG, null, null, null,
                version);
    }
}
