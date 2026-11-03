package com.fooddelivery.adminservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.adminservice.client.PlatformClient;
import com.fooddelivery.adminservice.client.PlatformService;
import com.fooddelivery.adminservice.client.RemoteRecords.AuditReplay;
import com.fooddelivery.adminservice.client.RemoteRecords.MenuItemStats;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.client.RemoteRecords.UserStats;
import com.fooddelivery.adminservice.client.StatsClient;
import com.fooddelivery.adminservice.entity.Role;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.admin.AdminActionAudited;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real MySQL and Kafka (Testcontainers). The tests publish audit events as the services
 * would; the services' statistics endpoints are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminServiceIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StatsClient statsClient;
    @MockitoBean
    private PlatformClient platformClient;

    @Test
    void everyServicesAuditRecordsAreStoredOnceAndCanBeSearched() throws Exception {
        String requestId = "req-" + UUID.randomUUID();
        String restaurantId = String.valueOf(randomId());
        long auditId = randomId();
        AdminActionAudited suspended = audited("restaurant-service", auditId, "RESTAURANT_SUSPENDED", "RESTAURANT",
                restaurantId, "ACTIVE", "SUSPENDED", "Policy violation: unsafe kitchen", requestId);
        publish(suspended);
        publish(audited("order-service", randomId(), "ORDER_CANCELLED_BY_ADMIN", "ORDER", String.valueOf(randomId()),
                "PLACED", "CANCELLED", "Restaurant suspended", requestId));
        publish(audited("payment-service", randomId(), "REFUND_INITIATED", "PAYMENT", String.valueOf(randomId()),
                null, "REFUND 250.00", "Order cancelled by admin", "req-" + UUID.randomUUID()));
        // Kafka delivers one again (a new event id, the same record): it is stored once
        publish(audited("restaurant-service", auditId, "RESTAURANT_SUSPENDED", "RESTAURANT", restaurantId,
                "ACTIVE", "SUSPENDED", "Policy violation: unsafe kitchen", requestId));

        await().atMost(TIMEOUT).untilAsserted(() -> search(Map.of("requestId", requestId))
                .andExpect(jsonPath("$.totalElements").value(2)));
        search(Map.of("service", "restaurant-service", "entityType", "RESTAURANT", "entityId", restaurantId))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("RESTAURANT_SUSPENDED"))
                .andExpect(jsonPath("$.content[0].sourceAuditId").value(auditId))
                .andExpect(jsonPath("$.content[0].oldValue").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("10.0.0.7"))
                .andExpect(jsonPath("$.content[0].adminUserId").value(1001));
        search(Map.of("requestId", requestId, "q", "POLICY")).andExpect(jsonPath("$.totalElements").value(1));
        search(Map.of("requestId", requestId, "fromDate", TODAY.plusDays(1).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
        search(Map.of("requestId", requestId, "sort", "reason,asc")).andExpect(status().isBadRequest());

        // Details: what else the same admin request did
        String page = search(Map.of("service", "restaurant-service", "entityId", restaurantId))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(page, "$.content[0].auditId")).longValue();
        asAdmin(get("/api/v1/admin/audit-logs/{id}", id), null)
                .andExpect(jsonPath("$.record.reason").value("Policy violation: unsafe kitchen"))
                .andExpect(jsonPath("$.sameRequest[0].service").value("order-service"))
                .andExpect(jsonPath("$.sameRequest[0].action").value("ORDER_CANCELLED_BY_ADMIN"));
        asAdmin(get("/api/v1/admin/audit-logs/{id}", 999_999_999L), null).andExpect(status().isNotFound());
        asAdmin(get("/api/v1/admin/audit-logs/actions"), null)
                .andExpect(jsonPath("$[?(@.service == 'restaurant-service' && @.action == 'RESTAURANT_SUSPENDED')]").exists());
    }

    @Test
    void auditRecordsCannotBeChangedOrDeletedEvenWithSql() throws Exception {
        String requestId = "req-" + UUID.randomUUID();
        publish(audited("user-service", randomId(), "ADMIN_USER_DEACTIVATED", "USER", "42", "ACTIVE", "INACTIVE",
                "Fraud report", requestId));
        await().atMost(TIMEOUT).until(() -> count(requestId) == 1);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_records SET reason = 'nothing happened' WHERE request_id = ?", requestId))
                .hasMessageContaining("Audit records are immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_records WHERE request_id = ?", requestId))
                .hasMessageContaining("Audit records are immutable");
        assertThat(jdbcTemplate.queryForObject("SELECT reason FROM audit_records WHERE request_id = ?", String.class, requestId))
                .isEqualTo("Fraud report");
    }

    @Test
    void theDashboardShowsWhatAnsweredAndNamesWhatDidNot() throws Exception {
        when(statsClient.userStats(any(), any(), any()))
                .thenReturn(new UserStats(TODAY, TODAY, Map.of(), 25_430, 25_000, 430, 120, Map.of()));
        when(statsClient.menuItemStats(any())).thenReturn(new MenuItemStats(1_200, 1_100, 100, 1_000, 100));
        when(statsClient.orderStats(any(), any(), any())).thenReturn(orderStats(TODAY.minusDays(6), 7));
        when(statsClient.restaurantStats(any(), any(), any()))
                .thenThrow(new RemoteServiceException("restaurant-service", new IllegalStateException("Connection refused")));

        asAdmin(get("/api/v1/admin/dashboard"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(TODAY.toString()))
                .andExpect(jsonPath("$.chartsFrom").value(TODAY.minusDays(6).toString()))
                .andExpect(jsonPath("$.summary.totalCustomers").value(25_430))
                .andExpect(jsonPath("$.summary.activeMenuItems").value(1_100))
                .andExpect(jsonPath("$.summary.todaysOrders").value(10))
                .andExpect(jsonPath("$.summary.todaysRevenue").value(1000.00))
                .andExpect(jsonPath("$.summary.totalRevenue").value(7000.00))
                .andExpect(jsonPath("$.summary.totalRestaurants").isEmpty())
                .andExpect(jsonPath("$.charts.ordersPerDay.length()").value(7))
                // Mocks without an answer return null: those parts are missing too, but only failures are named
                .andExpect(jsonPath("$.unavailable").value(org.hamcrest.Matchers.contains("restaurant-service")));

        // The services are asked as the admin who asked (they check the role themselves)
        verify(statsClient).userStats(eq(TODAY), eq(TODAY), argThat(caller ->
                caller.userId() == 1L && caller.role() == Role.PLATFORM_ADMIN));
    }

    @Test
    void theRevenueReportGroupsDaysIntoWeeks() throws Exception {
        LocalDate from = LocalDate.parse("2026-09-30");
        when(statsClient.orderStats(eq(from), eq(from.plusDays(14)), any())).thenReturn(orderStats(from, 15));

        asAdmin(get("/api/v1/admin/reports/revenue").param("fromDate", from.toString())
                .param("toDate", from.plusDays(14).toString()).param("groupBy", "WEEK"), null)
                .andExpect(jsonPath("$.periods[*].label")
                        .value(org.hamcrest.Matchers.contains("2026-W40", "2026-W41", "2026-W42")))
                .andExpect(jsonPath("$.periods[1].revenue").value(7000.00))
                .andExpect(jsonPath("$.revenue").value(15000.00))
                .andExpect(jsonPath("$.unavailable").isEmpty());
        asAdmin(get("/api/v1/admin/reports/orders").param("fromDate", "2025-01-01").param("toDate", "2026-06-30"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void theBackfillAsksEveryServiceAndIsAudited() throws Exception {
        for (PlatformService service : PlatformService.values()) {
            when(platformClient.postInternal(eq(service), any(), eq(AuditReplay.class)))
                    .thenReturn(new AuditReplay(service.serviceName(), 3));
        }
        when(platformClient.postInternal(eq(PlatformService.PROMOTION), any(), eq(AuditReplay.class)))
                .thenThrow(new RemoteServiceException("promotion-service", new IllegalStateException("Connection refused")));
        String reason = "Backfill " + UUID.randomUUID();

        asAdmin(post("/api/v1/admin/audit-logs/backfill"), "{\"reason\": \"" + reason + "\"}")
                .andExpect(jsonPath("$.republished.order-service").value(3))
                .andExpect(jsonPath("$.republished.length()").value(8))
                .andExpect(jsonPath("$.republished.delivery-partner-service").value(3))
                .andExpect(jsonPath("$.unavailable").value(org.hamcrest.Matchers.contains("promotion-service")));
        // admin-service's own action is in the log too, with what happened
        search(Map.of("service", "admin-service", "action", "AUDIT_LOG_BACKFILLED", "q", reason))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].newValue").value(org.hamcrest.Matchers.containsString("order-service=3")))
                .andExpect(jsonPath("$.content[0].newValue")
                        .value(org.hamcrest.Matchers.containsString("unavailable [promotion-service]")));
    }

    // ---- helpers ----

    private static OrderStats orderStats(LocalDate first, int days) {
        List<OrderStats.Day> perDay = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            perDay.add(new OrderStats.Day(first.plusDays(i), 10, 8, 1, new BigDecimal("1000.00")));
        }
        return new OrderStats(first, first.plusDays(days - 1L), "INR", 10L * days, 8L * days, days, days,
                new BigDecimal(1000 * days).setScale(2), BigDecimal.ZERO.setScale(2), new BigDecimal("125.00"),
                Map.of("DELIVERED", 8L * days), Map.of(), perDay,
                new OrderStats.Overall(10L * days, 8L * days, 5, new BigDecimal(1000 * days).setScale(2)));
    }

    private static AdminActionAudited audited(String service, long auditId, String action, String entityType,
                                              String entityId, String oldValue, String newValue, String reason,
                                              String requestId) {
        return AdminActionAudited.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now()).setRequestId(requestId)
                .setSourceService(service).setAuditId(auditId).setAdminUserId(1001L).setAction(action)
                .setEntityType(entityType).setEntityId(entityId).setOldValue(oldValue).setNewValue(newValue)
                .setReason(reason).setIpAddress("10.0.0.7")
                .build();
    }

    private void publish(AdminActionAudited event) throws Exception {
        kafkaTemplate.send(Topics.ADMIN_ACTION_AUDITED, event.getEntityType() + ":" + event.getEntityId(), event).get();
    }

    private long count(String requestId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_records WHERE request_id = ?", Long.class, requestId);
        return count == null ? 0 : count;
    }

    private ResultActions search(Map<String, String> filters) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/admin/audit-logs");
        filters.forEach(request::param);
        return asAdmin(request, null);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String json) throws Exception {
        request.with(TestTokens.as("1", "PLATFORM_ADMIN"));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    }
}
