package com.fooddelivery.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.fooddelivery.catalogservice.cache.MenuCache;
import com.fooddelivery.catalogservice.dto.request.CategoryRequest;
import com.fooddelivery.catalogservice.dto.response.CategoryResponse;
import com.fooddelivery.catalogservice.entity.AuditAction;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.CategoryStatus;
import com.fooddelivery.catalogservice.entity.Role;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.mapper.CategoryMapper;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "10.0.0.1", "req-1");

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private AdminAuditService adminAuditService;

    @Mock
    private CatalogEventPublisher catalogEventPublisher;

    @Mock
    private MenuCache menuCache;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, menuItemRepository, new CategoryMapper(), adminAuditService,
                catalogEventPublisher, menuCache);
    }

    @Test
    void newCategoryGoesToTheEndOfTheOrderAndIsAudited() {
        when(categoryRepository.existsByName("Rolls")).thenReturn(false);
        when(categoryRepository.findMaxDisplayOrder()).thenReturn(8);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(9L);
            return category;
        });

        CategoryResponse response = categoryService.createCategory(new CategoryRequest(" Rolls ", null, null), ADMIN);

        assertThat(response.displayOrder()).isEqualTo(9);
        assertThat(response.status()).isEqualTo(CategoryStatus.ACTIVE);
        verify(adminAuditService).recordAction(eq(ADMIN), eq(AuditAction.CATEGORY_CREATED), eq("CATEGORY"), eq(9L),
                isNull(), eq("name=Rolls; description=null"), isNull());
    }

    @Test
    void duplicateCategoryNameIsAConflict() {
        when(categoryRepository.existsByName("Pizza")).thenReturn(true);

        var categoryRequest = new CategoryRequest("Pizza", null, null);
        assertThatThrownBy(() -> categoryService.createCategory(categoryRequest, ADMIN))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        Category pizza = givenCategory(1L, "Pizza", 1);
        when(menuItemRepository.countByCategoryId(1L)).thenReturn(12L);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L, "Cleanup", ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("used by 12 menu item(s)");
        verify(categoryRepository, never()).delete(pizza);
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void unusedCategoryIsDeletedAndAudited() {
        Category rolls = givenCategory(9L, "Rolls", 9);
        when(menuItemRepository.countByCategoryId(9L)).thenReturn(0L);

        categoryService.deleteCategory(9L, "Created by mistake", ADMIN);

        verify(categoryRepository).delete(rolls);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.CATEGORY_DELETED, "CATEGORY", 9L,
                "name=Rolls; description=null", null, "Created by mistake");
    }

    @Test
    void reorderMustListEveryCategoryExactlyOnce() {
        Category pizza = category(1L, "Pizza", 1);
        Category burgers = category(2L, "Burgers", 2);
        when(categoryRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(pizza, burgers));

        var duplicated = List.of(2L, 2L);
        assertThatThrownBy(() -> categoryService.reorderCategories(duplicated, ADMIN))
                .isInstanceOf(BusinessRuleException.class);
        var incomplete = List.of(2L);
        assertThatThrownBy(() -> categoryService.reorderCategories(incomplete, ADMIN))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reorderAssignsPositionsInTheGivenOrder() {
        Category pizza = category(1L, "Pizza", 1);
        Category burgers = category(2L, "Burgers", 2);
        when(categoryRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(pizza, burgers));
        when(menuItemRepository.countItemsByCategoryIds(anyList())).thenReturn(List.of());

        List<CategoryResponse> response = categoryService.reorderCategories(List.of(2L, 1L), ADMIN);

        assertThat(response).extracting(CategoryResponse::name).containsExactly("Burgers", "Pizza");
        assertThat(burgers.getDisplayOrder()).isEqualTo(1);
        assertThat(pizza.getDisplayOrder()).isEqualTo(2);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.CATEGORIES_REORDERED, "CATEGORY", "ALL", "1,2", "2,1", null);
    }

    @Test
    void deactivatingAnInactiveCategoryIsAConflict() {
        Category desserts = givenCategory(7L, "Desserts", 7);
        desserts.setStatus(CategoryStatus.INACTIVE);

        assertThatThrownBy(() -> categoryService.deactivateCategory(7L, "Again", ADMIN))
                .isInstanceOf(ConflictException.class);
    }

    private Category givenCategory(Long id, String name, int displayOrder) {
        Category category = category(id, name, displayOrder);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        return category;
    }

    private static Category category(Long id, String name, int displayOrder) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setStatus(CategoryStatus.ACTIVE);
        return category;
    }
}
