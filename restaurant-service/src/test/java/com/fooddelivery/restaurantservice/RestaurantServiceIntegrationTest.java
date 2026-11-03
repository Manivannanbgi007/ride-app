package com.fooddelivery.restaurantservice;

import static com.fooddelivery.restaurantservice.TestData.restaurantRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.restaurantservice.client.UserServiceClient;
import com.fooddelivery.restaurantservice.client.UserSummary;
import com.fooddelivery.restaurantservice.dto.request.RestaurantRequest;
import com.fooddelivery.restaurantservice.exception.RemoteServiceException;
import com.fooddelivery.restaurantservice.repository.AdminAuditLogRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full application against a real MySQL and Kafka (Testcontainers). Only user-service is replaced by a mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RestaurantServiceIntegrationTest {

    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Test
    void onboardingFromSubmissionToActiveAndThenSuspended() throws Exception {
        long ownerId = randomOwnerId();
        String name = uniqueName("Spice Hub");
        when(userServiceClient.findUser(ownerId)).thenReturn(Optional.of(
                new UserSummary(ownerId, "Arjun Mehta", "arjun@example.com", "+919800000000", "RESTAURANT_ADMIN", "ACTIVE")));

        // 1. Owner submits: PENDING, invisible to customers
        long restaurantId = submit(ownerId, restaurantRequest(name));
        mockMvc.perform(get("/api/v1/restaurants/{id}", restaurantId))
                .andExpect(status().isNotFound());

        // 2. Admin finds it among pending approvals and reviews it, with owner details from user-service
        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants").param("status", "PENDING").param("search", name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/{id}", restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.foodLicenseNumber").value("11223344556677"))
                .andExpect(jsonPath("$.restaurant.documents[0].type").value("FOOD_LICENSE"))
                .andExpect(jsonPath("$.owner.lookupStatus").value("FOUND"))
                .andExpect(jsonPath("$.owner.fullName").value("Arjun Mehta"));

        // 3. Approve, then go live
        changeStatus(restaurantId, "approve", "License verified")
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").exists());
        changeStatus(restaurantId, "activate", "Ready to take orders")
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 4. Customers can now see it, without business or owner data
        mockMvc.perform(get("/api/v1/restaurants/{id}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.operatingHours.length()").value(7))
                .andExpect(jsonPath("$.openNow").isBoolean())
                .andExpect(jsonPath("$.legalBusinessName").doesNotExist())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());
        mockMvc.perform(get("/api/v1/restaurants").param("search", name).param("city", "bengaluru").param("cuisine", "biryani"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // 5. Suspended: hidden from customers again
        changeStatus(restaurantId, "suspend", "Repeated hygiene complaints")
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(get("/api/v1/restaurants/{id}", restaurantId))
                .andExpect(status().isNotFound());

        // 6. Every admin decision is in the audit log, newest first
        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/{id}/audit-logs", restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].action").value("RESTAURANT_SUSPENDED"))
                .andExpect(jsonPath("$.content[1].action").value("RESTAURANT_ACTIVATED"))
                .andExpect(jsonPath("$.content[2].action").value("RESTAURANT_APPROVED"))
                .andExpect(jsonPath("$.content[2].adminUserId").value(ADMIN_ID));
    }

    @Test
    void rejectedRestaurantIsFixedAndResubmitted() throws Exception {
        long ownerId = randomOwnerId();
        long restaurantId = submit(ownerId, restaurantRequest(uniqueName("Dosa Corner")));

        changeStatus(restaurantId, "reject", "License photo is unreadable")
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // The owner sees why it was rejected
        mockMvc.perform(asOwner(ownerId, get("/api/v1/restaurants/mine/{id}", restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusReason").value("License photo is unreadable"));

        // Fix the data (current version 1 after the rejection), then resubmit
        mockMvc.perform(asOwner(ownerId, put("/api/v1/restaurants/mine/{id}", restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(restaurantRequest(uniqueName("Dosa Corner"), 1L))))
                .andExpect(status().isOk());
        mockMvc.perform(asOwner(ownerId, put("/api/v1/restaurants/mine/{id}/resubmit", restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusReason").doesNotExist());

        // Every change was published in order, each with the version after the change
        List<RestaurantChanged> events = KafkaTestEvents.awaitEvents(consumerFactory, Topics.RESTAURANT_CHANGED,
                String.valueOf(restaurantId), RestaurantChanged.class, 4);
        assertThat(events).extracting(RestaurantChanged::getChangeType)
                .containsExactly("SUBMITTED", "STATUS_CHANGED", "PROFILE_UPDATED", "STATUS_CHANGED");
        assertThat(events).extracting(RestaurantChanged::getVersion).containsExactly(0L, 1L, 2L, 3L);
        assertThat(events).allSatisfy(event -> assertThat(event.getOwnerUserId()).isEqualTo(ownerId));
        assertThat(events.get(1).getStatus()).isEqualTo("REJECTED");
        assertThat(events.get(1).getReason()).isEqualTo("License photo is unreadable");
        assertThat(events.get(3).getPreviousStatus()).isEqualTo("REJECTED");
        assertThat(events.get(3).getStatus()).isEqualTo("PENDING");
        // Since event-contracts 1.3.0 the event carries the pickup address for delivery partners
        assertThat(events.getFirst().getAddress().getLine1()).isEqualTo("45 Brigade Road");
        assertThat(events.getFirst().getAddress().getCity()).isEqualTo("Bengaluru");
        // Since 1.4.0 also what restaurant search needs
        assertThat(events.getFirst().getCuisines()).containsExactly("Biryani", "North Indian");
        assertThat(events.getFirst().getDescription()).isEqualTo("Hyderabadi biryani and kebabs");
    }

    @Test
    void thePublicPageIsCachedAndNewOpeningHoursShowAtOnce() throws Exception {
        long ownerId = randomOwnerId();
        long restaurantId = submit(ownerId, restaurantRequest(uniqueName("Cache Corner")));
        changeStatus(restaurantId, "approve", "Documents verified").andExpect(status().isOk());
        changeStatus(restaurantId, "activate", "Ready").andExpect(status().isOk());

        // Open every day (11:00-23:00): cached on the first read
        mockMvc.perform(get("/api/v1/restaurants/{id}", restaurantId))
                .andExpect(jsonPath("$.operatingHours.length()").value(7))
                .andExpect(jsonPath("$.openNow").isBoolean());
        assertThat(redis.keys("restaurant:v1:details:" + restaurantId + ":*")).hasSize(1);

        // Only open tomorrow from now on: the next read shows the new hours, and "open now" follows them
        java.time.DayOfWeek tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1).getDayOfWeek();
        mockMvc.perform(asOwner(ownerId, put("/api/v1/restaurants/mine/{id}/operating-hours", restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 2, "operatingHours": [{"dayOfWeek": "%s", "opensAt": "00:00", "closesAt": "23:59"}]}
                                """.formatted(tomorrow)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/restaurants/{id}", restaurantId))
                .andExpect(jsonPath("$.operatingHours.length()").value(1))
                .andExpect(jsonPath("$.openNow").value(false));
    }

    @Test
    void anAdminRepublishesEveryRestaurantWithoutChangingIt() throws Exception {
        long ownerId = randomOwnerId();
        long restaurantId = submit(ownerId, restaurantRequest(uniqueName("Replay Kitchen")));

        mockMvc.perform(asAdmin(post("/api/v1/admin/restaurants/events/republish")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.republished").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        List<RestaurantChanged> events = KafkaTestEvents.awaitEvents(consumerFactory, Topics.RESTAURANT_CHANGED,
                String.valueOf(restaurantId), RestaurantChanged.class, 2);
        assertThat(events.get(1).getChangeType()).isEqualTo("REPUBLISHED");
        // Same version: consumers that already have it change nothing
        assertThat(events.get(1).getVersion()).isEqualTo(events.getFirst().getVersion());
        assertThat(adminAuditLogRepository.findByEntityTypeAndEntityId("RESTAURANT", "ALL", PageRequest.of(0, 50)))
                .extracting(auditLog -> auditLog.getAction().name()).contains("RESTAURANT_EVENTS_REPUBLISHED");
        mockMvc.perform(asOwner(ownerId, post("/api/v1/admin/restaurants/events/republish")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingAnOutdatedCopyIsRejectedWithConflict() throws Exception {
        long ownerId = randomOwnerId();
        long restaurantId = submit(ownerId, restaurantRequest(uniqueName("Tandoor House")));

        // First edit based on version 0 succeeds and moves the restaurant to version 1
        mockMvc.perform(asOwner(ownerId, put("/api/v1/restaurants/mine/{id}", restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(restaurantRequest("Tandoor House Edit A", 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // A second edit still based on version 0 must not silently overwrite it
        mockMvc.perform(asOwner(ownerId, put("/api/v1/restaurants/mine/{id}", restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(restaurantRequest("Tandoor House Edit B", 0L))))
                .andExpect(status().isConflict());
    }

    @Test
    void ownersCannotSeeEachOthersRestaurants() throws Exception {
        long restaurantId = submit(randomOwnerId(), restaurantRequest(uniqueName("Private Kitchen")));

        mockMvc.perform(asOwner(randomOwnerId(), get("/api/v1/restaurants/mine/{id}", restaurantId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalApiReturnsOwnerAndStatusForOtherServices() throws Exception {
        long ownerId = randomOwnerId();
        long restaurantId = submit(ownerId, restaurantRequest(uniqueName("Internal Check")));

        mockMvc.perform(get("/api/v1/internal/restaurants/{id}", restaurantId).with(TestTokens.serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(ownerId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.openNow").isBoolean())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.address.city").value("Bengaluru"))
                .andExpect(jsonPath("$.foodLicenseNumber").doesNotExist());

        mockMvc.perform(get("/api/v1/internal/restaurants/{id}", 999_999_999L).with(TestTokens.serviceToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOwnersRestaurantsAreListedForOtherServicesOnly() throws Exception {
        long ownerId = randomOwnerId();
        long first = submit(ownerId, restaurantRequest(uniqueName("Owner's First")));
        long second = submit(ownerId, restaurantRequest(uniqueName("Owner's Second")));
        submit(randomOwnerId(), restaurantRequest(uniqueName("Someone Else's")));

        mockMvc.perform(get("/api/v1/internal/restaurants").param("ownerUserId", String.valueOf(ownerId))
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.containsInAnyOrder((int) first, (int) second)));
        // Without the services' shared token (even as that owner): nothing
        mockMvc.perform(asOwner(ownerId, get("/api/v1/internal/restaurants").param("ownerUserId", String.valueOf(ownerId))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/internal/restaurants").param("ownerUserId", String.valueOf(ownerId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminReviewStillWorksWhenUserServiceIsDown() throws Exception {
        long ownerId = randomOwnerId();
        long restaurantId = submit(ownerId, restaurantRequest(uniqueName("Cafe Mocha")));
        when(userServiceClient.findUser(ownerId))
                .thenThrow(new RemoteServiceException("user-service", new RuntimeException("Connection refused")));

        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/{id}", restaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.status").value("PENDING"))
                .andExpect(jsonPath("$.owner.lookupStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.owner.userId").value(ownerId));
    }

    @Test
    void registrationsAreCountedPerBusinessDayAroundLocalMidnight() throws Exception {
        long marker = randomOwnerId();
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.LocalDate day = java.time.LocalDate.parse("2019-03-10"); // years back: no other test writes here
        // 10 March from local 00:00 (still 9 March in UTC) to 23:59; the minute before and after belong to the neighbours
        insertRestaurant(marker, "PENDING", day.atTime(0, 0).atZone(zone).toInstant());
        insertRestaurant(marker, "ACTIVE", day.atTime(12, 0).atZone(zone).toInstant());
        insertRestaurant(marker, "REJECTED", day.atTime(23, 59).atZone(zone).toInstant());
        insertRestaurant(marker, "PENDING", day.minusDays(1).atTime(23, 59).atZone(zone).toInstant());
        insertRestaurant(marker, "PENDING", day.plusDays(1).atTime(0, 0).atZone(zone).toInstant());
        insertRestaurant(marker, "PENDING", day.plusDays(30).atTime(10, 0).atZone(zone).toInstant());

        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/stats/registrations")
                        .param("from", "2019-03-10").param("to", "2019-03-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].date").value("2019-03-10"))
                .andExpect(jsonPath("$.days[0].registered").value(3));
        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/stats/registrations")
                        .param("from", "2019-03-09").param("to", "2019-03-11")))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2019-03-09"))
                .andExpect(jsonPath("$.days[2].date").value("2019-03-11"));
        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/stats/registrations")
                        .param("from", "2019-03-10").param("to", "2019-03-09")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/stats/registrations")
                        .param("from", "2019-01-01").param("to", "2020-02-05")))
                .andExpect(status().isBadRequest()); // 401 days
        mockMvc.perform(asAdmin(get("/api/v1/admin/restaurants/stats/registrations").param("from", "2019-03-10")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asOwner(marker, get("/api/v1/admin/restaurants/stats/registrations")
                        .param("from", "2019-03-10").param("to", "2019-03-10")))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("DELETE FROM restaurants WHERE owner_user_id = ?", marker);
    }

    /** A restaurant created at a chosen time (the API cannot back-date submissions). */
    private void insertRestaurant(long ownerId, String status, java.time.Instant createdAt) {
        java.time.LocalDateTime created = java.time.LocalDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC); // stored as UTC
        jdbcTemplate.update("INSERT INTO restaurants (owner_user_id, name, cuisines, phone, email, address_line1, city,"
                + " state, postal_code, country, time_zone, legal_business_name, food_license_number, status,"
                + " status_changed_at, created_at, updated_at)"
                + " VALUES (?, ?, 'Indian', '9999999999', 'a@b.c', 'x', 'c', 's', 'p', 'k', 'Asia/Kolkata', 'L', 'F',"
                + " ?, ?, ?, ?)",
                ownerId, uniqueName("Back-dated"), status, created, created, created);
    }

    private long submit(long ownerId, RestaurantRequest request) throws Exception {
        String body = mockMvc.perform(asOwner(ownerId, post("/api/v1/restaurants/mine"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(long restaurantId, String action, String reason)
            throws Exception {
        return mockMvc.perform(asAdmin(put("/api/v1/admin/restaurants/{id}/" + action, restaurantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "%s"}
                                """.formatted(reason)))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder asOwner(long ownerId, MockHttpServletRequestBuilder request) {
        return request.with(TestTokens.as(ownerId, "RESTAURANT_ADMIN"));
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.with(TestTokens.as(ADMIN_ID, "PLATFORM_ADMIN"));
    }

    private String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private static long randomOwnerId() {
        return ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    }

    private static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
