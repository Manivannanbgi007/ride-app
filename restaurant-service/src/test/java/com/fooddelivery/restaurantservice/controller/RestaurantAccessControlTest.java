package com.fooddelivery.restaurantservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fooddelivery.restaurantservice.TestTokens;
import com.fooddelivery.restaurantservice.config.SecurityConfig;
import com.fooddelivery.restaurantservice.dto.response.PageResponse;
import com.fooddelivery.restaurantservice.service.AdminRestaurantDetailsService;
import com.fooddelivery.restaurantservice.service.AdminRestaurantService;
import com.fooddelivery.restaurantservice.service.PublicRestaurantService;
import com.fooddelivery.restaurantservice.service.RestaurantEventReplayService;
import com.fooddelivery.restaurantservice.service.RestaurantOwnerService;
import com.fooddelivery.restaurantservice.service.RestaurantStatsService;
import com.fooddelivery.restaurantservice.web.SecurityProblemHandlers;
import com.fooddelivery.restaurantservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call which API area, and request validation. */
@WebMvcTest({PublicRestaurantController.class, RestaurantOwnerController.class, AdminRestaurantController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class RestaurantAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicRestaurantService publicRestaurantService;

    @MockitoBean
    private RestaurantOwnerService restaurantOwnerService;

    @MockitoBean
    private AdminRestaurantService adminRestaurantService;

    @MockitoBean
    private RestaurantEventReplayService restaurantEventReplayService;

    @MockitoBean
    private AdminRestaurantDetailsService adminRestaurantDetailsService;

    @MockitoBean
    private RestaurantStatsService restaurantStatsService;

    @Test
    void publicListNeedsNoIdentity() throws Exception {
        when(publicRestaurantService.browseRestaurants(any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/restaurants"))
                .andExpect(status().isOk());
    }

    @Test
    void ownerApiWithoutRoleHeaderIs401() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/mine").header("X-User-Id", "5"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(restaurantOwnerService);
    }

    @Test
    void customerCannotUseOwnerApi() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/mine")
                        .with(TestTokens.as("5", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not allowed to use this API."));
    }

    @Test
    void restaurantOwnerCannotUseAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/restaurants")
                        .with(TestTokens.as("5", "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminRestaurantService);
    }

    @Test
    void submissionWithMissingFieldsListsEveryProblem() throws Exception {
        mockMvc.perform(post("/api/v1/restaurants/mine")
                        .with(TestTokens.as("5", "RESTAURANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "cuisines": ["Pizza, Pasta"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors['cuisines[0]']").exists())
                .andExpect(jsonPath("$.errors.address").exists())
                .andExpect(jsonPath("$.errors.documents").exists())
                .andExpect(jsonPath("$.errors.operatingHours").exists());

        verifyNoInteractions(restaurantOwnerService);
    }
}
