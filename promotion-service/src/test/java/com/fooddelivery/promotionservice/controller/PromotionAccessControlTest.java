package com.fooddelivery.promotionservice.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.promotionservice.TestTokens;
import com.fooddelivery.promotionservice.config.SecurityConfig;
import com.fooddelivery.promotionservice.service.CouponAdminService;
import com.fooddelivery.promotionservice.service.CouponRedemptionService;
import com.fooddelivery.promotionservice.service.OfferService;
import com.fooddelivery.promotionservice.web.SecurityProblemHandlers;
import com.fooddelivery.promotionservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call what, and validation. */
@WebMvcTest({AdminCouponController.class, OfferController.class, InternalCouponController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class PromotionAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponAdminService couponAdminService;
    @MockitoBean
    private OfferService offerService;
    @MockitoBean
    private CouponRedemptionService couponRedemptionService;

    @Test
    void offersAreForEveryone() throws Exception {
        mockMvc.perform(get("/api/v1/coupons")).andExpect(status().isOk());
    }

    @Test
    void onlyPlatformAdminsManageCoupons() throws Exception {
        mockMvc.perform(get("/api/v1/admin/coupons").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/coupons")).andExpect(status().isUnauthorized());
        verifyNoInteractions(couponAdminService);
    }

    @Test
    void aCouponNeedsValidTerms() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "no spaces!", "discountType": "FIXED", "discountValue": 0, "usageLimit": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.discountValue").exists())
                .andExpect(jsonPath("$.errors.validFrom").exists())
                .andExpect(jsonPath("$.errors.usageLimit").exists());
        verifyNoInteractions(couponAdminService);
    }
}
