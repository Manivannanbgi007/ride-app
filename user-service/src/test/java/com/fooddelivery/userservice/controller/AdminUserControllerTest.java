package com.fooddelivery.userservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.fooddelivery.userservice.TestTokens;
import com.fooddelivery.userservice.config.SecurityConfig;
import com.fooddelivery.userservice.dto.response.AdminUserDetailsResponse;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.exception.ConflictException;
import com.fooddelivery.userservice.exception.UserNotFoundException;
import com.fooddelivery.userservice.service.AdminUserService;
import com.fooddelivery.userservice.service.UserStatsService;
import com.fooddelivery.userservice.web.CallerInfo;
import com.fooddelivery.userservice.web.SecurityProblemHandlers;
import com.fooddelivery.userservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web layer only (no database): authorization headers, validation and error format.
 */
@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private UserStatsService userStatsService;

    // The real decoder (JwtKeyConfig) is not part of this slice; the tests sign callers in with jwt(), which never decodes
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void callerWithoutATokenGets401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication required"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void nonAdminCallerGets403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(TestTokens.as("5", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not allowed to use this API."));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void deactivateWithoutReasonIsAValidationError() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/5/deactivate")
                        .with(TestTokens.as("100", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.reason").exists());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void deactivatePassesTheCallingAdminToTheService() throws Exception {
        when(adminUserService.deactivateUser(eq(5L), eq("Fraud suspected"), any(CallerInfo.class)))
                .thenReturn(details(5L, UserStatus.INACTIVE));

        mockMvc.perform(put("/api/v1/admin/users/5/deactivate")
                        .with(TestTokens.as("100", "PLATFORM_ADMIN"))
                        .header("X-Request-Id", "req-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Fraud suspected"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-42"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(adminUserService).deactivateUser(5L, "Fraud suspected",
                new CallerInfo(100L, Role.PLATFORM_ADMIN, "127.0.0.1", "req-42"));
    }

    @Test
    void businessConflictBecomes409WithRequestId() throws Exception {
        when(adminUserService.activateUser(eq(5L), any(), any()))
                .thenThrow(new ConflictException("User 5 is already active"));

        mockMvc.perform(put("/api/v1/admin/users/5/activate")
                        .with(TestTokens.as("100", "PLATFORM_ADMIN"))
                        .header("X-Request-Id", "req-43")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Verified"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User 5 is already active"))
                .andExpect(jsonPath("$.requestId").value("req-43"));
    }

    @Test
    void onlyAdminsListAUsersAddresses() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/5/addresses").with(TestTokens.as("5", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users/5/addresses")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUserService);

        when(adminUserService.getUserAddresses(5L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/users/5/addresses").with(TestTokens.as("100", "PLATFORM_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        when(adminUserService.getUserAddresses(404L)).thenThrow(new UserNotFoundException(404L));
        mockMvc.perform(get("/api/v1/admin/users/404/addresses")
                        .with(TestTokens.as("100", "PLATFORM_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private static AdminUserDetailsResponse details(Long id, UserStatus status) {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        return new AdminUserDetailsResponse(id, "user@example.com", "User", null, Role.CUSTOMER, status,
                now, now, null, 0);
    }
}
