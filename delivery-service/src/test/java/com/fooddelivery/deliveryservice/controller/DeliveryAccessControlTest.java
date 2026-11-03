package com.fooddelivery.deliveryservice.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.deliveryservice.TestTokens;
import com.fooddelivery.deliveryservice.config.SecurityConfig;
import com.fooddelivery.deliveryservice.service.AdminDeliveryService;
import com.fooddelivery.deliveryservice.service.AdminPartnerService;
import com.fooddelivery.deliveryservice.service.PartnerDeliveryService;
import com.fooddelivery.deliveryservice.service.PartnerRosterService;
import com.fooddelivery.deliveryservice.service.PartnerService;
import com.fooddelivery.deliveryservice.service.PartnerStatsService;
import com.fooddelivery.deliveryservice.service.RestaurantPartnersService;
import com.fooddelivery.deliveryservice.service.RiderLocationService;
import com.fooddelivery.deliveryservice.service.TrackingService;
import com.fooddelivery.deliveryservice.web.SecurityProblemHandlers;
import com.fooddelivery.deliveryservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call what, and validation. */
@WebMvcTest({PartnerController.class, PartnerDeliveryController.class, TrackingController.class,
        AdminPartnerController.class, AdminDeliveryController.class, RestaurantDeliveryController.class,
        InternalPartnerController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class DeliveryAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PartnerService partnerService;
    @MockitoBean
    private PartnerRosterService partnerRosterService;
    @MockitoBean
    private PartnerDeliveryService partnerDeliveryService;
    @MockitoBean
    private TrackingService trackingService;
    @MockitoBean
    private AdminPartnerService adminPartnerService;
    @MockitoBean
    private AdminDeliveryService adminDeliveryService;
    @MockitoBean
    private PartnerStatsService partnerStatsService;
    @MockitoBean
    private RestaurantPartnersService restaurantPartnersService;
    @MockitoBean
    private RiderLocationService riderLocationService;

    @Test
    void onlyThePartnerSharesALiveLocationAndOnlyWithValidCoordinates() throws Exception {
        String position = "{\"latitude\": 12.97, \"longitude\": 77.59}";
        mockMvc.perform(put("/api/v1/delivery/deliveries/mine/{id}/location", 5)
                        .with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(position))
                .andExpect(status().isForbidden());
        for (String invalid : new String[] {"{\"latitude\": 91, \"longitude\": 77.59}",
            "{\"latitude\": 12.97, \"longitude\": -181}", "{\"latitude\": 12.97}",
            "{\"latitude\": 12.97, \"longitude\": 77.59, \"headingDeg\": 400}"}) {
            mockMvc.perform(put("/api/v1/delivery/deliveries/mine/{id}/location", 5)
                            .with(TestTokens.as("9", "DELIVERY_PARTNER"))
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(riderLocationService);

        mockMvc.perform(put("/api/v1/delivery/deliveries/mine/{id}/location", 5)
                        .with(TestTokens.as("9", "DELIVERY_PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(position))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyCustomersSeeTheLiveMap() throws Exception {
        for (String role : new String[] {"DELIVERY_PARTNER", "RESTAURANT_ADMIN"}) {
            mockMvc.perform(get("/api/v1/delivery/orders/{id}/live", 42)
                            .with(TestTokens.as("9", role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(trackingService);
    }

    @Test
    void onlyDeliveryPartnersUseThePartnerApi() throws Exception {
        mockMvc.perform(put("/api/v1/delivery/deliveries/mine/{id}/accept", 5)
                        .with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/delivery/partners/me/availability").header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"AVAILABLE\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(partnerService, partnerDeliveryService);
    }

    @Test
    void onlyCustomersTrackDeliveries() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/orders/{id}", 42)
                        .with(TestTokens.as("9", "DELIVERY_PARTNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(trackingService);
    }

    @Test
    void onlyPlatformAdminsManagePartnersAndDeliveries() throws Exception {
        mockMvc.perform(get("/api/v1/admin/deliveries").with(TestTokens.as("9", "DELIVERY_PARTNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/delivery-partners/availability")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminPartnerService, adminDeliveryService);
    }

    @Test
    void goingOnlineNeedsAStatus() throws Exception {
        mockMvc.perform(put("/api/v1/delivery/partners/me/availability")
                        .with(TestTokens.as("9", "DELIVERY_PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
        mockMvc.perform(put("/api/v1/delivery/partners/me/availability")
                        .with(TestTokens.as("9", "DELIVERY_PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ON_BREAK\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(partnerService);
    }

    @Test
    void theInternalPartnerListIsReadInPagesOfAtMost500() throws Exception {
        mockMvc.perform(get("/api/v1/internal/delivery-partners")
                        .with(TestTokens.serviceToken())
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());
        verify(partnerRosterService).getRoster(2, 50);
        mockMvc.perform(get("/api/v1/internal/delivery-partners")
                        .with(TestTokens.serviceToken())
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/internal/delivery-partners")
                        .with(TestTokens.serviceToken())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aReassignmentNeedsAReason() throws Exception {
        mockMvc.perform(put("/api/v1/admin/deliveries/{id}/reassign", 5)
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\": 9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        verifyNoInteractions(adminDeliveryService);
    }

    @Test
    void onlyTheRestaurantOwnerRoleReadsThePartnersOfARestaurant() throws Exception {
        for (String role : new String[] {"CUSTOMER", "PLATFORM_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(get("/api/v1/restaurants/mine/10/deliveries/partners")
                            .with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/restaurants/mine/10/deliveries/partners")).andExpect(status().isUnauthorized());
        verifyNoInteractions(restaurantPartnersService);

        mockMvc.perform(get("/api/v1/restaurants/mine/10/deliveries/partners").param("from", "2026-10-01")
                        .param("to", "2026-10-07").param("limit", "20")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isOk());
        verify(restaurantPartnersService).getPartners(3L, 10L, java.time.LocalDate.parse("2026-10-01"),
                java.time.LocalDate.parse("2026-10-07"), 20);
        mockMvc.perform(get("/api/v1/restaurants/mine/10/deliveries/partners")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isOk());
        verify(restaurantPartnersService).getPartners(3L, 10L, null, null, 5);
    }

    @Test
    void thePartnersLimitMustBeBetween1And20() throws Exception {
        for (String limit : new String[] {"0", "21", "x"}) {
            mockMvc.perform(get("/api/v1/restaurants/mine/10/deliveries/partners").param("limit", limit)
                            .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/restaurants/mine/10/deliveries/partners").param("to", "07-10-2026")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(restaurantPartnersService);
    }
}
