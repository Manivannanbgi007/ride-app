package com.fooddelivery.catalogservice.controller;

import java.util.List;

import com.fooddelivery.catalogservice.dto.response.InternalMenuItemResponse;
import com.fooddelivery.catalogservice.service.InternalMenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. The API Gateway has no route for /api/v1/internal/**,
 * so these endpoints are reachable only from inside the platform network.
 */
@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/menu-items")
@RequiredArgsConstructor
public class InternalMenuItemController {

    private final InternalMenuItemService internalMenuItemService;

    @Operation(summary = "Current data of several menu items in one call (unknown ids are left out)")
    @GetMapping
    public List<InternalMenuItemResponse> getMenuItems(@RequestParam @Size(min = 1, max = 100) List<Long> ids) {
        return internalMenuItemService.getMenuItems(ids);
    }
}
