package com.fooddelivery.catalogservice.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fooddelivery.catalogservice.cache.MenuCache;
import com.fooddelivery.catalogservice.dto.request.CategoryRequest;
import com.fooddelivery.catalogservice.dto.request.CategorySearchCriteria;
import com.fooddelivery.catalogservice.dto.response.AuditLogResponse;
import com.fooddelivery.catalogservice.dto.response.CategoryResponse;
import com.fooddelivery.catalogservice.dto.response.PageResponse;
import com.fooddelivery.catalogservice.dto.response.PublicCategoryResponse;
import com.fooddelivery.catalogservice.entity.AuditAction;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.CategoryStatus;
import com.fooddelivery.catalogservice.exception.BusinessRuleException;
import com.fooddelivery.catalogservice.exception.ConflictException;
import com.fooddelivery.catalogservice.exception.ResourceNotFoundException;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.CategoryChange;
import com.fooddelivery.catalogservice.mapper.CategoryMapper;
import com.fooddelivery.catalogservice.repository.CatalogSpecifications;
import com.fooddelivery.catalogservice.repository.CategoryItemCount;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.validation.SortValidator;
import com.fooddelivery.catalogservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Global food categories: customers read them, platform admins manage them. Admin changes are audited. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final List<String> SORTABLE_FIELDS = List.of("displayOrder", "name", "createdAt", "status");

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final CategoryMapper categoryMapper;
    private final AdminAuditService adminAuditService;
    private final CatalogEventPublisher catalogEventPublisher;
    private final MenuCache menuCache;

    /** Served from Redis when cached (see MenuCache), otherwise read from MySQL. */
    @Transactional(readOnly = true)
    public List<PublicCategoryResponse> getActiveCategories() {
        return menuCache.activeCategories(() -> categoryRepository.findByStatusOrderByDisplayOrderAsc(CategoryStatus.ACTIVE)
                .stream()
                .map(categoryMapper::toPublicResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> searchCategories(CategorySearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);

        List<Specification<Category>> conditions = new ArrayList<>();
        if (StringUtils.hasText(criteria.search())) {
            conditions.add(CatalogSpecifications.categoryNameContains(criteria.search()));
        }
        if (criteria.status() != null) {
            conditions.add(CatalogSpecifications.categoryHasStatus(criteria.status()));
        }

        Page<Category> categories = categoryRepository.findAll(Specification.allOf(conditions), pageable);
        Map<Long, Long> itemCounts = itemCounts(categories.getContent());

        return PageResponse.from(categories.map(category ->
                categoryMapper.toResponse(category, itemCounts.getOrDefault(category.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(Long categoryId) {
        Category category = findCategory(categoryId);
        return categoryMapper.toResponse(category, menuItemRepository.countByCategoryId(categoryId));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request, CallerInfo admin) {
        if (categoryRepository.existsByName(request.name().trim())) {
            throw new ConflictException("A category named '" + request.name().trim() + "' already exists");
        }

        Category category = new Category();
        categoryMapper.applyRequest(category, request);
        category.setDisplayOrder(categoryRepository.findMaxDisplayOrder() + 1);
        category.setStatus(CategoryStatus.ACTIVE);
        Category savedCategory = categoryRepository.saveAndFlush(category);
        catalogEventPublisher.categoryChanged(savedCategory, CategoryChange.CREATED);

        adminAuditService.recordAction(admin, AuditAction.CATEGORY_CREATED, AdminAuditService.ENTITY_TYPE_CATEGORY,
                savedCategory.getId(), null, describe(savedCategory), null);
        log.info("Category created. categoryId={} name={}", savedCategory.getId(), savedCategory.getName());

        return categoryMapper.toResponse(savedCategory, 0);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request, CallerInfo admin) {
        Category category = findCategory(categoryId);
        if (categoryRepository.existsByNameAndIdNot(request.name().trim(), categoryId)) {
            throw new ConflictException("A category named '" + request.name().trim() + "' already exists");
        }

        String before = describe(category);
        categoryMapper.applyRequest(category, request);
        categoryRepository.flush();
        catalogEventPublisher.categoryChanged(category, CategoryChange.UPDATED);

        adminAuditService.recordAction(admin, AuditAction.CATEGORY_UPDATED, AdminAuditService.ENTITY_TYPE_CATEGORY,
                categoryId, before, describe(category), null);
        return categoryMapper.toResponse(category, menuItemRepository.countByCategoryId(categoryId));
    }

    @Transactional
    public CategoryResponse activateCategory(Long categoryId, String reason, CallerInfo admin) {
        return changeStatus(categoryId, CategoryStatus.ACTIVE, AuditAction.CATEGORY_ACTIVATED, reason, admin);
    }

    /** Existing menu items keep the category; it just cannot be chosen for items any more and is hidden from customers. */
    @Transactional
    public CategoryResponse deactivateCategory(Long categoryId, String reason, CallerInfo admin) {
        return changeStatus(categoryId, CategoryStatus.INACTIVE, AuditAction.CATEGORY_DEACTIVATED, reason, admin);
    }

    /** A category that menu items still use cannot be deleted (deactivate it instead). */
    @Transactional
    public void deleteCategory(Long categoryId, String reason, CallerInfo admin) {
        Category category = findCategory(categoryId);

        long usedBy = menuItemRepository.countByCategoryId(categoryId);
        if (usedBy > 0) {
            throw new ConflictException("Category '" + category.getName() + "' is used by " + usedBy
                    + " menu item(s). Deactivate it instead, or move the items to another category first.");
        }

        catalogEventPublisher.categoryChanged(category, CategoryChange.DELETED);
        categoryRepository.delete(category);
        adminAuditService.recordAction(admin, AuditAction.CATEGORY_DELETED, AdminAuditService.ENTITY_TYPE_CATEGORY,
                categoryId, describe(category), null, reason);
        log.info("Category deleted. categoryId={} name={}", categoryId, category.getName());
    }

    /** Sets the display order. The request must list every category exactly once. */
    @Transactional
    public List<CategoryResponse> reorderCategories(List<Long> orderedIds, CallerInfo admin) {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        Set<Long> existingIds = categories.stream().map(Category::getId).collect(Collectors.toSet());

        if (orderedIds.size() != existingIds.size() || !new HashSet<>(orderedIds).equals(existingIds)) {
            throw new BusinessRuleException("The new order must contain every category id exactly once: " + existingIds);
        }

        String before = categories.stream().map(category -> String.valueOf(category.getId())).collect(Collectors.joining(","));
        Map<Long, Category> byId = categories.stream().collect(Collectors.toMap(Category::getId, category -> category));
        List<Category> moved = new ArrayList<>();
        for (int position = 0; position < orderedIds.size(); position++) {
            Category category = byId.get(orderedIds.get(position));
            if (category.getDisplayOrder() != position + 1) {
                category.setDisplayOrder(position + 1);
                moved.add(category);
            }
        }
        categoryRepository.flush();
        moved.forEach(category -> catalogEventPublisher.categoryChanged(category, CategoryChange.REORDERED));

        String after = orderedIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        adminAuditService.recordAction(admin, AuditAction.CATEGORIES_REORDERED, AdminAuditService.ENTITY_TYPE_CATEGORY,
                "ALL", before, after, null);

        List<Category> reordered = orderedIds.stream().map(byId::get).toList();
        Map<Long, Long> itemCounts = itemCounts(reordered);
        return reordered.stream()
                .map(category -> categoryMapper.toResponse(category, itemCounts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    /** Audit history survives deletion, so this does not require the category to still exist. */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getCategoryAuditLogs(Long categoryId, Pageable pageable) {
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_CATEGORY, categoryId, pageable);
    }

    private CategoryResponse changeStatus(Long categoryId, CategoryStatus newStatus, AuditAction action,
                                          String reason, CallerInfo admin) {
        Category category = findCategory(categoryId);
        CategoryStatus oldStatus = category.getStatus();
        if (oldStatus == newStatus) {
            throw new ConflictException("Category '" + category.getName() + "' is already " + newStatus);
        }

        category.setStatus(newStatus);
        categoryRepository.flush();
        catalogEventPublisher.categoryChanged(category, CategoryChange.STATUS_CHANGED);

        adminAuditService.recordAction(admin, action, AdminAuditService.ENTITY_TYPE_CATEGORY, categoryId,
                oldStatus.name(), newStatus.name(), reason);
        log.info("Category status changed. categoryId={} oldStatus={} newStatus={} adminUserId={}",
                categoryId, oldStatus, newStatus, admin.userId());

        return categoryMapper.toResponse(category, menuItemRepository.countByCategoryId(categoryId));
    }

    private Map<Long, Long> itemCounts(List<Category> categories) {
        if (categories.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = categories.stream().map(Category::getId).toList();
        return menuItemRepository.countItemsByCategoryIds(ids).stream()
                .collect(Collectors.toMap(CategoryItemCount::getCategoryId, CategoryItemCount::getItemCount));
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    private static String describe(Category category) {
        return "name=" + category.getName() + "; description=" + category.getDescription();
    }
}
