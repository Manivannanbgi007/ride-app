package com.fooddelivery.catalogservice.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fooddelivery.catalogservice.TestTokens;
import com.fooddelivery.catalogservice.config.SecurityConfig;
import com.fooddelivery.catalogservice.service.AdminMenuItemEditService;
import com.fooddelivery.catalogservice.service.AdminMenuItemService;
import com.fooddelivery.catalogservice.service.CatalogEventReplayService;
import com.fooddelivery.catalogservice.service.CategoryService;
import com.fooddelivery.catalogservice.service.MenuItemStatsService;
import com.fooddelivery.catalogservice.service.OwnerMenuService;
import com.fooddelivery.catalogservice.service.PublicMenuService;
import com.fooddelivery.catalogservice.web.SecurityProblemHandlers;
import com.fooddelivery.catalogservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call which API area, and request validation. */
@WebMvcTest({PublicCatalogController.class, OwnerMenuController.class,
        AdminCategoryController.class, AdminMenuItemController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class CatalogAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private PublicMenuService publicMenuService;

    @MockitoBean
    private OwnerMenuService ownerMenuService;

    @MockitoBean
    private AdminMenuItemService adminMenuItemService;

    @MockitoBean
    private AdminMenuItemEditService adminMenuItemEditService;

    @MockitoBean
    private CatalogEventReplayService catalogEventReplayService;

    @MockitoBean
    private MenuItemStatsService menuItemStatsService;

    @Test
    void customersReadCategoriesWithoutIdentity() throws Exception {
        when(categoryService.getActiveCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotManageAMenu() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/mine/10/menu-items")
                        .with(TestTokens.as("5", "CUSTOMER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ownerMenuService);
    }

    @Test
    void restaurantOwnerCannotManageGlobalCategories() throws Exception {
        mockMvc.perform(post("/api/v1/admin/categories")
                        .with(TestTokens.as("5", "RESTAURANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Rolls"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void invalidMenuItemListsEveryProblem() throws Exception {
        mockMvc.perform(post("/api/v1/restaurants/mine/10/menu-items")
                        .with(TestTokens.as("5", "RESTAURANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": -5, "imageUrl": "not a url"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists())
                .andExpect(jsonPath("$.errors.foodType").exists())
                .andExpect(jsonPath("$.errors.imageUrl").exists());

        verifyNoInteractions(ownerMenuService);
    }

    @Test
    void onlyPlatformAdminsCanCreateEditAndDuplicateMenuItemsOfAnyRestaurant() throws Exception {
        String body = """
                {"restaurantId": 10, "name": "Biryani", "price": 99, "categoryId": 3, "foodType": "VEG", "version": 0}
                """;
        for (String role : List.of("CUSTOMER", "RESTAURANT_ADMIN")) {
            mockMvc.perform(post("/api/v1/admin/menu-items").with(TestTokens.as("5", role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/admin/menu-items/20").with(TestTokens.as("5", role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/menu-items/20/duplicate").with(TestTokens.as("5", role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/menu-items/category-stats").with(TestTokens.as("5", role)))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(adminMenuItemEditService, menuItemStatsService);
    }

    @Test
    void menuItemEditingNeedsAnIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/admin/menu-items/20/duplicate"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/menu-items/category-stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingredientsAndNutritionAreLimitedTo1000Characters() throws Exception {
        String tooLong = "x".repeat(1001);
        mockMvc.perform(post("/api/v1/restaurants/mine/10/menu-items")
                        .with(TestTokens.as("5", "RESTAURANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\": \"" + tooLong + "\", \"nutritionInfo\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ingredients").exists())
                .andExpect(jsonPath("$.errors.nutritionInfo").exists());

        mockMvc.perform(post("/api/v1/admin/menu-items")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\": \"" + tooLong + "\", \"nutritionInfo\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ingredients").exists())
                .andExpect(jsonPath("$.errors.nutritionInfo").exists());

        verifyNoInteractions(ownerMenuService, adminMenuItemEditService);
    }

    @Test
    void adminMenuItemCreateListsEveryProblemAndCategoryStatsLimitIsChecked() throws Exception {
        mockMvc.perform(post("/api/v1/admin/menu-items")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.restaurantId").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists())
                .andExpect(jsonPath("$.errors.foodType").exists());

        mockMvc.perform(get("/api/v1/admin/menu-items/category-stats").param("limit", "13")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminMenuItemEditService, menuItemStatsService);
    }
}
