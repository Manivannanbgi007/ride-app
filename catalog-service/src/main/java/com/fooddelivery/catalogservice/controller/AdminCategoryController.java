package com.fooddelivery.catalogservice.controller;

import java.net.URI;
import java.util.List;

import com.fooddelivery.catalogservice.dto.request.CategoryOrderRequest;
import com.fooddelivery.catalogservice.dto.request.CategoryRequest;
import com.fooddelivery.catalogservice.dto.request.CategorySearchCriteria;
import com.fooddelivery.catalogservice.dto.request.StatusChangeRequest;
import com.fooddelivery.catalogservice.dto.response.AuditLogResponse;
import com.fooddelivery.catalogservice.dto.response.CategoryResponse;
import com.fooddelivery.catalogservice.dto.response.PageResponse;
import com.fooddelivery.catalogservice.dto.response.RepublishResponse;
import com.fooddelivery.catalogservice.service.CatalogEventReplayService;
import com.fooddelivery.catalogservice.service.CategoryService;
import com.fooddelivery.catalogservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform admins managing global food categories. Access restricted to PLATFORM_ADMIN. */
@Tag(name = "Admin - Categories", description = "Global food categories")
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final CatalogEventReplayService catalogEventReplayService;

    @Operation(summary = "List categories with item counts (search, status, paging)")
    @GetMapping
    public PageResponse<CategoryResponse> searchCategories(
            @Valid @ParameterObject CategorySearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 50, sort = "displayOrder") Pageable pageable) {
        return categoryService.searchCategories(criteria, pageable);
    }

    @Operation(summary = "One category")
    @GetMapping("/{categoryId}")
    public CategoryResponse getCategory(@PathVariable Long categoryId) {
        return categoryService.getCategory(categoryId);
    }

    @Operation(summary = "Create a category (added at the end of the order)")
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request, CallerInfo caller) {
        CategoryResponse category = categoryService.createCategory(request, caller);
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + category.id())).body(category);
    }

    @Operation(summary = "Rename / update a category")
    @PutMapping("/{categoryId}")
    public CategoryResponse updateCategory(@PathVariable Long categoryId,
                                           @Valid @RequestBody CategoryRequest request,
                                           CallerInfo caller) {
        return categoryService.updateCategory(categoryId, request, caller);
    }

    @Operation(summary = "Activate a category")
    @PutMapping("/{categoryId}/activate")
    public CategoryResponse activateCategory(@PathVariable Long categoryId,
                                             @Valid @RequestBody StatusChangeRequest request,
                                             CallerInfo caller) {
        return categoryService.activateCategory(categoryId, request.reason(), caller);
    }

    @Operation(summary = "Deactivate a category (existing items keep it; it can no longer be chosen)")
    @PutMapping("/{categoryId}/deactivate")
    public CategoryResponse deactivateCategory(@PathVariable Long categoryId,
                                               @Valid @RequestBody StatusChangeRequest request,
                                               CallerInfo caller) {
        return categoryService.deactivateCategory(categoryId, request.reason(), caller);
    }

    @Operation(summary = "Set the display order (send every category id once, in the new order)")
    @PutMapping("/order")
    public List<CategoryResponse> reorderCategories(@Valid @RequestBody CategoryOrderRequest request, CallerInfo caller) {
        return categoryService.reorderCategories(request.categoryIds(), caller);
    }

    @Operation(summary = "Delete a category that no menu item uses (409 otherwise)")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId,
                                               @RequestParam(required = false) @Size(max = 500) String reason,
                                               CallerInfo caller) {
        categoryService.deleteCategory(categoryId, reason, caller);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Audit history of a category (newest first)")
    @GetMapping("/{categoryId}/audit-logs")
    public PageResponse<AuditLogResponse> getCategoryAuditLogs(@PathVariable Long categoryId,
                                                               @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return categoryService.getCategoryAuditLogs(categoryId, pageable);
    }

    @Operation(summary = "Publish every category again as a CategoryChanged event (audited)",
            description = "For consumers that need to catch up, for example after rebuilding the search index. "
                    + "Changes no data; consumers ignore versions they already have.")
    @PostMapping("/events/republish")
    public RepublishResponse republishEvents(CallerInfo caller) {
        return catalogEventReplayService.republishCategories(caller);
    }
}
