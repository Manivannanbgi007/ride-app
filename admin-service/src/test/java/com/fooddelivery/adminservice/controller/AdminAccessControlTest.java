package com.fooddelivery.adminservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.fooddelivery.adminservice.TestTokens;
import com.fooddelivery.adminservice.config.SecurityConfig;
import com.fooddelivery.adminservice.dto.request.CalendarEventType;
import com.fooddelivery.adminservice.dto.response.CalendarResponse;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.adminservice.service.AuditBackfillService;
import com.fooddelivery.adminservice.service.AuditLogService;
import com.fooddelivery.adminservice.service.CalendarService;
import com.fooddelivery.adminservice.service.DashboardService;
import com.fooddelivery.adminservice.service.ReportService;
import com.fooddelivery.adminservice.web.SecurityProblemHandlers;
import com.fooddelivery.adminservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: everything here is for platform admins, and parameters are checked. */
@WebMvcTest({AdminDashboardController.class, AdminReportController.class, AdminAuditLogController.class,
        AdminCalendarController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class AdminAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private AuditLogService auditLogService;
    @MockitoBean
    private AuditBackfillService auditBackfillService;
    @MockitoBean
    private CalendarService calendarService;

    @Test
    void onlyPlatformAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/dashboard/order-activity").param("fromDate", "2026-10-01")
                .param("toDate", "2026-10-01").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reports/revenue").with(TestTokens.as("7", "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit-logs").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/calendar").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/calendar").param("from", "2026-10-01").param("to", "2026-10-31")
                .with(TestTokens.as("7", "RESTAURANT_ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/calendar").param("from", "2026-10-01").param("to", "2026-10-31")
                .with(TestTokens.as("7", "CUSTOMER"))).andExpect(status().isForbidden());
        verifyNoInteractions(dashboardService, reportService, auditLogService, calendarService);
    }

    @Test
    void parametersAreChecked() throws Exception {
        mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard").param("days", "0"))).andExpect(status().isBadRequest());
        // The order charts need both days, written as dates
        mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard/order-activity").param("fromDate", "2026-10-01")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/v1/admin/dashboard/order-activity")
                .param("fromDate", "yesterday").param("toDate", "2026-10-01"))).andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/v1/admin/reports/revenue").param("groupBy", "YEAR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/v1/admin/reports/orders").param("limit", "500"))).andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(post("/api/v1/admin/audit-logs/backfill")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        // The calendar needs both days, written as dates, and known types
        mockMvc.perform(asAdmin(get("/api/v1/admin/calendar").param("from", "2026-10-01"))).andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/v1/admin/calendar").param("from", "soon").param("to", "2026-10-31")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/v1/admin/calendar").param("from", "2026-10-01").param("to", "2026-10-31")
                .param("types", "ORDERS,PAYMENTS"))).andExpect(status().isBadRequest());
        verifyNoInteractions(dashboardService, reportService, auditBackfillService, calendarService);
    }

    @Test
    void theCalendarPassesTheRangeAndTypesOnAndAnswersAsJson() throws Exception {
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate to = LocalDate.parse("2026-10-31");
        when(calendarService.getCalendar(eq(from), eq(to), anyCollection(), any())).thenReturn(
                new CalendarResponse(from, to, "Asia/Kolkata", List.of(CalendarEventType.COUPONS), List.of()));

        mockMvc.perform(asAdmin(get("/api/v1/admin/calendar").param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("types", "ORDERS,RESTAURANTS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.unavailable[0]").value("COUPONS"))
                .andExpect(jsonPath("$.events").isEmpty());
        verify(calendarService).getCalendar(eq(from), eq(to),
                eq(List.of(CalendarEventType.ORDERS, CalendarEventType.RESTAURANTS)), any());
    }

    @Test
    void theCalendarIs503WhenNoSourceAnswered() throws Exception {
        when(calendarService.getCalendar(any(), any(), any(), any()))
                .thenThrow(new RemoteServiceException("order-service", new IllegalStateException("down")));

        mockMvc.perform(asAdmin(get("/api/v1/admin/calendar").param("from", "2026-10-01").param("to", "2026-10-31")))
                .andExpect(status().isServiceUnavailable());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.with(TestTokens.as("1", "PLATFORM_ADMIN"));
    }
}
