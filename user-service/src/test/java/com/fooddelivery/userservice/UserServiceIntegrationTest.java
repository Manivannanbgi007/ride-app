package com.fooddelivery.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.user.UserRegistered;
import com.fooddelivery.userservice.repository.OutboxEventRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full application against a real MySQL and Kafka (Testcontainers): Flyway migrations, JPA mappings,
 * transactions, published events and the HTTP API working together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    // Created at startup by AdminBootstrap from application-test.yml
    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "AdminPass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void registrationPublishesUserRegisteredEvent() throws Exception {
        String email = uniqueEmail("kavya");
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-Id", "it-register-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Secret1234", "fullName": "Kavya Rao",
                                 "role": "DELIVERY_PARTNER"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        long userId = login(email, "Secret1234");

        UserRegistered event = KafkaTestEvents.awaitEvent(
                consumerFactory, Topics.USER_REGISTERED, String.valueOf(userId), UserRegistered.class);

        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getEmail()).isEqualTo(email);
        assertThat(event.getFullName()).isEqualTo("Kavya Rao");
        assertThat(event.getRole()).isEqualTo("DELIVERY_PARTNER");
        assertThat(event.getRequestId()).isEqualTo("it-register-001");
        assertThat(event.getEventId()).isNotBlank();

        // It went through the transactional outbox: one row, marked published once Kafka acknowledged it
        // (the relay commits that mark just after the acknowledgement, so wait for it)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("USER", userId))
                        .singleElement()
                        .satisfies(outboxEvent -> {
                            assertThat(outboxEvent.getEventId()).isEqualTo(event.getEventId());
                            assertThat(outboxEvent.getPublishedAt()).isNotNull();
                        }));
    }

    @Test
    void aRejectedRegistrationWritesNoEvent() throws Exception {
        String email = uniqueEmail("dup");
        register(email, "First Owner", "CUSTOMER");
        long eventsBefore = outboxEventRepository.count();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Secret1234", "fullName": "Second Try"}
                                """.formatted(email)))
                .andExpect(status().isConflict());

        assertThat(outboxEventRepository.count()).isEqualTo(eventsBefore);
    }

    @Test
    void customerRegistersAddsAddressAndIsDeactivatedByAdmin() throws Exception {
        long adminId = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = uniqueEmail("priya");

        // Customer signs up and logs in
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Priya1234", "fullName": "Priya Sharma"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        long customerId = login(email, "Priya1234");

        // First address becomes the default
        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .with(TestTokens.as(customerId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "HOME", "line1": "12 MG Road", "city": "Bengaluru",
                                 "state": "Karnataka", "postalCode": "560001", "country": "India"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true));

        // Admin deactivates the customer
        mockMvc.perform(put("/api/v1/admin/users/{userId}/deactivate", customerId)
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN"))
                        .header("X-Request-Id", "it-request-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Suspicious activity"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.addressCount").value(1));

        // The admin reads the customer's addresses (even of a deactivated account); others may not; unknown user is 404
        mockMvc.perform(get("/api/v1/admin/users/{userId}/addresses", customerId)
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].line1").value("12 MG Road"))
                .andExpect(jsonPath("$[0].city").value("Bengaluru"))
                .andExpect(jsonPath("$[0].defaultAddress").value(true));
        mockMvc.perform(get("/api/v1/admin/users/{userId}/addresses", customerId)
                        .with(TestTokens.as(customerId, "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users/{userId}/addresses", customerId + 100_000)
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN")))
                .andExpect(status().isNotFound());

        // Deactivated customer can no longer log in
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Priya1234"}
                                """.formatted(email)))
                .andExpect(status().isForbidden());

        // The admin action was audited, with who, what, why and the request id
        mockMvc.perform(get("/api/v1/admin/users/{userId}/audit-logs", customerId)
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("ADMIN_USER_DEACTIVATED"))
                .andExpect(jsonPath("$.content[0].adminUserId").value(adminId))
                .andExpect(jsonPath("$.content[0].oldValue").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].newValue").value("INACTIVE"))
                .andExpect(jsonPath("$.content[0].reason").value("Suspicious activity"))
                .andExpect(jsonPath("$.content[0].requestId").value("it-request-001"));
    }

    @Test
    void emailIsUniqueRegardlessOfCase() throws Exception {
        String email = uniqueEmail("ravi");
        register(email, "Ravi Kumar", "CUSTOMER");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Ravi12345", "fullName": "Ravi Again"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void adminSearchFiltersByTextRoleAndStatus() throws Exception {
        long adminId = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String nameToken = "Rider" + UUID.randomUUID().toString().substring(0, 8);
        register(uniqueEmail("rider"), nameToken + " One", "DELIVERY_PARTNER");

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN"))
                        .param("search", nameToken)
                        .param("role", "DELIVERY_PARTNER")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].role").value("DELIVERY_PARTNER"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN"))
                        .param("search", nameToken)
                        .param("role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void internalApiReturnsBasicUserDataForOtherServices() throws Exception {
        String email = uniqueEmail("owner");
        register(email, "Arjun Mehta", "RESTAURANT_ADMIN");
        long ownerId = login(email, "Secret1234");

        mockMvc.perform(get("/api/v1/internal/users/{userId}", ownerId).with(TestTokens.serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Arjun Mehta"))
                .andExpect(jsonPath("$.role").value("RESTAURANT_ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/internal/users/{userId}", 999_999_999L).with(TestTokens.serviceToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalApiReturnsAnAddressOnlyToItsOwner() throws Exception {
        String email = uniqueEmail("diner");
        register(email, "Meera Iyer", "CUSTOMER");
        long customerId = login(email, "Secret1234");
        String created = mockMvc.perform(post("/api/v1/users/me/addresses")
                        .with(TestTokens.as(customerId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "HOME", "line1": "7 Residency Road", "city": "Bengaluru",
                                 "state": "Karnataka", "postalCode": "560025", "country": "India"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long addressId = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(get("/api/v1/internal/users/{userId}/addresses/{addressId}", customerId, addressId)
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line1").value("7 Residency Road"))
                .andExpect(jsonPath("$.userId").value(customerId));

        mockMvc.perform(get("/api/v1/internal/users/{userId}/addresses/{addressId}", customerId + 1, addressId)
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sortingBySensitiveFieldIsRejected() throws Exception {
        long adminId = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(TestTokens.as(adminId, "PLATFORM_ADMIN"))
                        .param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest());
    }

    private void register(String email, String fullName, String role) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Secret1234", "fullName": "%s", "role": "%s"}
                                """.formatted(email, fullName, role)))
                .andExpect(status().isCreated());
    }

    private long login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.userId")).longValue();
    }

    private static String uniqueEmail(String name) {
        return name + "-" + UUID.randomUUID() + "@example.com";
    }
}
