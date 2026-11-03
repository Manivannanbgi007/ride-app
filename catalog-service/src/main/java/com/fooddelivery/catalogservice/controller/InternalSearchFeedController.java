package com.fooddelivery.catalogservice.controller;

import com.fooddelivery.catalogservice.dto.response.SourceVersionsResponse;
import com.fooddelivery.catalogservice.service.SearchFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API for search-service's consistency check. The API Gateway has no route for
 * /api/v1/internal/**, so these endpoints are reachable only from inside the platform network.
 */
@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/search-feed")
@RequiredArgsConstructor
public class InternalSearchFeedController {

    private final SearchFeedService searchFeedService;

    @Operation(summary = "Id and version of every category, in id order, one page at a time",
            description = "afterId = 0 for the first page, then nextAfterId.")
    @GetMapping("/categories")
    public SourceVersionsResponse getCategoryVersions(@RequestParam(defaultValue = "0") @Min(0) long afterId,
                                                      @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int size) {
        return searchFeedService.getCategoryVersions(afterId, size);
    }

    @Operation(summary = "Id and version of every menu item, in id order, one page at a time")
    @GetMapping("/menu-items")
    public SourceVersionsResponse getMenuItemVersions(@RequestParam(defaultValue = "0") @Min(0) long afterId,
                                                      @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int size) {
        return searchFeedService.getMenuItemVersions(afterId, size);
    }
}
