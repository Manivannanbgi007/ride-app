package com.fooddelivery.notificationservice.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.notificationservice.TestTokens;
import com.fooddelivery.notificationservice.config.SecurityConfig;
import com.fooddelivery.notificationservice.service.AdminNotificationService;
import com.fooddelivery.notificationservice.service.InboxService;
import com.fooddelivery.notificationservice.web.SecurityProblemHandlers;
import com.fooddelivery.notificationservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call what, and validation. */
@WebMvcTest({InboxController.class, AdminNotificationController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class NotificationAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboxService inboxService;
    @MockitoBean
    private AdminNotificationService adminNotificationService;

    @Test
    void theInboxNeedsToKnowWhoIsAsking() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inboxService);
    }

    @Test
    void everyRoleHasAnInbox() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyPlatformAdminsSeeEveryonesNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/notifications").header("X-User-Id", "7"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminNotificationService);
    }

    @Test
    void aResendNeedsAReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notifications/{id}/resend", 1)
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        verifyNoInteractions(adminNotificationService);
    }
}
