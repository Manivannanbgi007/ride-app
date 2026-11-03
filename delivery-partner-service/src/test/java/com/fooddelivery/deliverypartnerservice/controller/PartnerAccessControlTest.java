package com.fooddelivery.deliverypartnerservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.deliverypartnerservice.TestTokens;
import com.fooddelivery.deliverypartnerservice.config.SecurityConfig;
import com.fooddelivery.deliverypartnerservice.service.AdminPartnerService;
import com.fooddelivery.deliverypartnerservice.service.LegacyPartnerImportService;
import com.fooddelivery.deliverypartnerservice.service.PartnerProfileService;
import com.fooddelivery.deliverypartnerservice.web.SecurityProblemHandlers;
import com.fooddelivery.deliverypartnerservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call what, and validation. */
@WebMvcTest({PartnerProfileController.class, AdminPartnerController.class, InternalPartnerController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class PartnerAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PartnerProfileService partnerProfileService;
    @MockitoBean
    private AdminPartnerService adminPartnerService;
    @MockitoBean
    private LegacyPartnerImportService legacyPartnerImportService;

    @Test
    void onlyDeliveryPartnersUseTheProfileApi() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/partners/me").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/delivery/partners/me").header("X-User-Id", "7"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/delivery/partners/me").header("X-User-Role", "DELIVERY_PARTNER"))
                .andExpect(status().isUnauthorized());
        // A valid token of a role that is not a delivery partner's
        mockMvc.perform(get("/api/v1/delivery/partners/me").with(TestTokens.as("7", "COURIER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(partnerProfileService);
    }

    @Test
    void onlyPlatformAdminsManagePartners() throws Exception {
        mockMvc.perform(get("/api/v1/admin/delivery-partners").with(TestTokens.as("9", "DELIVERY_PARTNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/delivery-partners/{id}/deactivate", 9)
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminPartnerService);
    }

    @Test
    void aProfileNeedsVehicleAndCity() throws Exception {
        mockMvc.perform(put("/api/v1/delivery/partners/me")
                        .with(TestTokens.as("9", "DELIVERY_PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\": \"HOVERBOARD\", \"vehicleNumber\": \"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/delivery/partners/me")
                        .with(TestTokens.as("9", "DELIVERY_PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\": \"SCOOTER\", \"vehicleNumber\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.vehicleNumber").exists())
                .andExpect(jsonPath("$.errors.city").exists());
        verifyNoInteractions(partnerProfileService);
    }

    @Test
    void suspendingAndReactivatingNeedAReason() throws Exception {
        for (String action : new String[] {"activate", "deactivate"}) {
            mockMvc.perform(put("/api/v1/admin/delivery-partners/{id}/" + action, 9)
                            .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \" \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.reason").exists());
        }
        verifyNoInteractions(adminPartnerService);

        mockMvc.perform(put("/api/v1/admin/delivery-partners/{id}/deactivate", 9)
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .header("X-Request-Id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Fraud check\"}"))
                .andExpect(status().isOk());
        verify(adminPartnerService).deactivate(eq(9L), eq("Fraud check"), any());
    }

    @Test
    void theInternalApiNeedsTheServicesTokenNotAUser() throws Exception {
        mockMvc.perform(get("/api/v1/internal/delivery-partners/{id}", 9))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/internal/delivery-partners/{id}", 9)
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isOk());
        verify(partnerProfileService).getForDelivery(9L);
    }
}
