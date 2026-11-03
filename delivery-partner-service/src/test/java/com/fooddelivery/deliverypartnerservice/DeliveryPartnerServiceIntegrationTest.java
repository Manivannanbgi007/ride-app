package com.fooddelivery.deliverypartnerservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.deliverypartnerservice.client.DeliveryServiceClient;
import com.fooddelivery.deliverypartnerservice.client.DeliveryServiceClient.LegacyPartner;
import com.fooddelivery.deliverypartnerservice.client.DeliveryServiceClient.LegacyPartnerPage;
import com.fooddelivery.deliverypartnerservice.client.UserServiceClient;
import com.fooddelivery.deliverypartnerservice.client.UserServiceClient.UserSummary;
import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import com.fooddelivery.deliverypartnerservice.entity.PartnerStatus;
import com.fooddelivery.deliverypartnerservice.exception.RemoteServiceException;
import com.fooddelivery.deliverypartnerservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliverypartnerservice.repository.OutboxEventRepository;
import com.fooddelivery.deliverypartnerservice.repository.ProcessedEventRepository;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.admin.AdminActionAudited;
import com.fooddelivery.events.delivery.DeliveryPartnerStatusChanged;
import com.fooddelivery.events.partner.DeliveryPartnerChanged;
import com.fooddelivery.events.user.UserRegistered;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real MySQL and Kafka (Testcontainers). user-service and delivery-service are mocked;
 * their events are published to Kafka by the tests. Each test uses its own (random) partners and city.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DeliveryPartnerServiceIntegrationTest {

    private static final long ADMIN = 1L;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    /** Users as user-service would report them (partners registered on their first call). */
    private final Map<Long, UserSummary> users = new ConcurrentHashMap<>();
    private volatile boolean userServiceDown;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;
    @Autowired
    private DeliveryPartnerRepository deliveryPartnerRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private DeliveryServiceClient deliveryServiceClient;

    private String city;

    @BeforeEach
    void setUp() {
        city = "City-" + UUID.randomUUID().toString().substring(0, 8);
        when(userServiceClient.findUser(anyLong())).thenAnswer(invocation -> {
            if (userServiceDown) {
                throw new RemoteServiceException("user-service", new IllegalStateException("Connection refused"));
            }
            return Optional.ofNullable(users.get(invocation.<Long>getArgument(0)));
        });
    }

    @Test
    void aPartnerIsRegisteredFromTheEventAndAnnouncedToDeliveryService() throws Exception {
        long partner = randomId();
        publish(Topics.USER_REGISTERED, partner, userRegistered(partner, "Ravi Kumar", "DELIVERY_PARTNER"));
        await().atMost(TIMEOUT).until(() -> deliveryPartnerRepository.existsById(partner));

        asPartner(partner, get("/api/v1/delivery/partners/me"), null)
                .andExpect(jsonPath("$.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.phone").value("+919800000001"))
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.readyToWork").value(false))
                .andExpect(jsonPath("$.completedDeliveries").value(0));

        DeliveryPartnerChanged registered = KafkaTestEvents.awaitEvent(consumerFactory, Topics.DELIVERY_PARTNER_CHANGED,
                String.valueOf(partner), DeliveryPartnerChanged.class);
        assertThat(registered.getChangeType()).isEqualTo("REGISTERED");
        assertThat(registered.getVersion()).isZero();
        assertThat(registered.getFullName()).isEqualTo("Ravi Kumar");
        assertThat(registered.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(registered.getCity()).isNull();

        // Customers and restaurant owners are not delivery partners. Events with one key are read in order, so once
        // the partner's event (sent after it) is processed, the customer's has been too.
        long another = randomId();
        long customer = randomId();
        publish(Topics.USER_REGISTERED, another, userRegistered(customer, "Priya Sharma", "CUSTOMER"));
        UserRegistered partnerRegistered = userRegistered(another, "Anil Das", "DELIVERY_PARTNER");
        publish(Topics.USER_REGISTERED, another, partnerRegistered);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(partnerRegistered.getEventId()));
        assertThat(deliveryPartnerRepository.existsById(customer)).isFalse();

        // The same event delivered twice, or another registration event of the same partner, announces them once
        publish(Topics.USER_REGISTERED, another, partnerRegistered);
        UserRegistered again = userRegistered(another, "Anil Das", "DELIVERY_PARTNER");
        publish(Topics.USER_REGISTERED, another, again);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(again.getEventId()));
        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("DELIVERY_PARTNER", another))
                .hasSize(1);
    }

    @Test
    void aPartnerCompletesTheProfileAndDeliveryServiceLearnsTheNewVersion() throws Exception {
        long partner = knownPartner("Ravi Kumar");
        asPartner(partner, get("/api/v1/delivery/partners/me"), null)
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        asPartner(partner, put("/api/v1/delivery/partners/me"),
                "{\"vehicleType\": \"MOTORCYCLE\", \"vehicleNumber\": \" ka01ab1234 \", \"city\": \" " + city + " \"}")
                .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.vehicleNumber").value("KA01AB1234"))
                .andExpect(jsonPath("$.city").value(city))
                .andExpect(jsonPath("$.readyToWork").value(true))
                .andExpect(jsonPath("$.version").value(1));
        // Saving the same profile again changes nothing and announces nothing
        asPartner(partner, put("/api/v1/delivery/partners/me"),
                "{\"vehicleType\": \"MOTORCYCLE\", \"vehicleNumber\": \"KA01AB1234\", \"city\": \"" + city + "\"}")
                .andExpect(jsonPath("$.version").value(1));

        List<DeliveryPartnerChanged> events = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.DELIVERY_PARTNER_CHANGED, String.valueOf(partner), DeliveryPartnerChanged.class, 2);
        assertThat(events).extracting(DeliveryPartnerChanged::getChangeType).containsExactly("REGISTERED", "PROFILE_UPDATED");
        assertThat(events.get(1).getVersion()).isEqualTo(1L);
        assertThat(events.get(1).getVehicleNumber()).isEqualTo("KA01AB1234");
        assertThat(events.get(1).getCity()).isEqualTo(city);

        // A customer account is not a delivery partner; user-service down is 503, never a guess
        long customer = randomId();
        users.put(customer, new UserSummary(customer, "Priya Sharma", "priya@example.com", null, "CUSTOMER", "ACTIVE"));
        asPartner(customer, get("/api/v1/delivery/partners/me"), null).andExpect(status().isNotFound());
        userServiceDown = true;
        asPartner(randomId(), get("/api/v1/delivery/partners/me"), null).andExpect(status().isServiceUnavailable());
    }

    @Test
    void theWorkStatusReportedByDeliveryServiceIsShownAndOlderReportsAreIgnored() throws Exception {
        long partner = knownPartner("Ravi Kumar");
        asPartner(partner, get("/api/v1/delivery/partners/me"), null).andExpect(status().isOk());

        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, partner, workStatus(partner, 5, "BUSY", 77L, 3));
        await().atMost(TIMEOUT).until(() -> partner(partner).getStatus() == PartnerStatus.BUSY);
        asPartner(partner, get("/api/v1/delivery/partners/me"), null)
                .andExpect(jsonPath("$.status").value("BUSY"))
                .andExpect(jsonPath("$.currentDeliveryId").value(77))
                .andExpect(jsonPath("$.completedDeliveries").value(3));

        // An older report (it was overtaken), a status this version does not know, and a repeated event change nothing
        DeliveryPartnerStatusChanged older = workStatus(partner, 4, "AVAILABLE", null, 2);
        DeliveryPartnerStatusChanged unknown = workStatus(partner, 6, "ON_BREAK", null, 3);
        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, partner, older);
        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, partner, unknown);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(unknown.getEventId()));
        assertThat(partner(partner).getStatus()).isEqualTo(PartnerStatus.BUSY);
        assertThat(partner(partner).getWorkStatusVersion()).isEqualTo(5L);

        DeliveryPartnerStatusChanged delivered = workStatus(partner, 7, "AVAILABLE", null, 4);
        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, partner, delivered);
        await().atMost(TIMEOUT).until(() -> partner(partner).getStatus() == PartnerStatus.AVAILABLE);
        assertThat(partner(partner).getCompletedDeliveries()).isEqualTo(4);
        assertThat(partner(partner).getAvailableSince()).isNotNull();

        // A report about a partner this service does not know creates nobody
        long stranger = randomId();
        DeliveryPartnerStatusChanged strangerStatus = workStatus(stranger, 1, "AVAILABLE", null, 0);
        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, stranger, strangerStatus);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(strangerStatus.getEventId()));
        assertThat(deliveryPartnerRepository.existsById(stranger)).isFalse();
    }

    @Test
    void anAdminSuspendsAPartnerOnlyWhenTheyAreNotOnADelivery() throws Exception {
        long partner = readyPartner("Ravi Kumar");
        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, partner, workStatus(partner, 1, "BUSY", 501L, 0));
        await().atMost(TIMEOUT).until(() -> partner(partner).getStatus() == PartnerStatus.BUSY);

        String reason = "{\"reason\": \"Repeated customer complaints\"}";
        asAdmin(put("/api/v1/admin/delivery-partners/{p}/deactivate", partner), reason)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Partner " + partner + " is on delivery 501: reassign it first"));

        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, partner, workStatus(partner, 2, "AVAILABLE", null, 0));
        await().atMost(TIMEOUT).until(() -> partner(partner).getStatus() == PartnerStatus.AVAILABLE);
        // The reassign dialog: available partners in the city
        asAdmin(get("/api/v1/admin/delivery-partners").param("status", "AVAILABLE").param("city", city), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(partner));

        asAdmin(put("/api/v1/admin/delivery-partners/{p}/deactivate", partner), reason)
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Repeated customer complaints"));
        asAdmin(put("/api/v1/admin/delivery-partners/{p}/deactivate", partner), reason).andExpect(status().isConflict());
        asAdmin(get("/api/v1/admin/delivery-partners").param("status", "SUSPENDED").param("city", city), null)
                .andExpect(jsonPath("$.totalElements").value(1));
        asAdmin(get("/api/v1/admin/delivery-partners").param("q", "ravi").param("city", city), null)
                .andExpect(jsonPath("$.totalElements").value(1));
        // delivery-service confirms (its own copy is suspended now): still suspended here
        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, partner, workStatus(partner, 3, "SUSPENDED", null, 0));
        await().atMost(TIMEOUT).until(() -> Long.valueOf(3L).equals(partner(partner).getWorkStatusVersion()));
        asPartner(partner, get("/api/v1/delivery/partners/me"), null).andExpect(jsonPath("$.status").value("SUSPENDED"));

        asAdmin(put("/api/v1/admin/delivery-partners/{p}/activate", partner), "{\"reason\": \"Review closed\"}")
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.statusReason").doesNotExist());
        asAdmin(put("/api/v1/admin/delivery-partners/{p}/activate", partner), "{\"reason\": \"Again\"}")
                .andExpect(status().isConflict());
        asAdmin(get("/api/v1/admin/delivery-partners/{p}/audit-logs", partner), null)
                .andExpect(jsonPath("$.content[*].action").value(org.hamcrest.Matchers.contains(
                        "DELIVERY_PARTNER_ACTIVATED", "DELIVERY_PARTNER_DEACTIVATED")))
                .andExpect(jsonPath("$.content[1].oldValue").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[1].reason").value("Repeated customer complaints"));

        // delivery-service and the platform-wide audit log learn about both decisions
        List<DeliveryPartnerChanged> events = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.DELIVERY_PARTNER_CHANGED, String.valueOf(partner), DeliveryPartnerChanged.class, 4);
        assertThat(events).extracting(DeliveryPartnerChanged::getChangeType)
                .containsExactly("REGISTERED", "PROFILE_UPDATED", "SUSPENDED", "ACTIVATED");
        assertThat(events.get(2).getAccountStatus()).isEqualTo("SUSPENDED");
        assertThat(events.get(2).getSuspensionReason()).isEqualTo("Repeated customer complaints");
        assertThat(events.get(3).getVersion()).isGreaterThan(events.get(2).getVersion());
        List<AdminActionAudited> audited = KafkaTestEvents.awaitEvents(consumerFactory, Topics.ADMIN_ACTION_AUDITED,
                "DELIVERY_PARTNER:" + partner, AdminActionAudited.class, 2);
        assertThat(audited).extracting(AdminActionAudited::getSourceService).containsOnly("delivery-partner-service");

        asAdmin(get("/api/v1/admin/delivery-partners/{p}", randomId()), null).andExpect(status().isNotFound());
        asAdmin(get("/api/v1/admin/delivery-partners").param("sort", "password"), null).andExpect(status().isBadRequest());
    }

    @Test
    void deliveryServiceAsksForAPartnerItHasNoCopyOf() throws Exception {
        long partner = knownPartner("Ravi Kumar");
        mockMvc.perform(get("/api/v1/internal/delivery-partners/{p}", partner).with(TestTokens.serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));
        assertThat(deliveryPartnerRepository.existsById(partner)).isTrue();

        long customer = randomId();
        users.put(customer, new UserSummary(customer, "Priya Sharma", "priya@example.com", null, "CUSTOMER", "ACTIVE"));
        mockMvc.perform(get("/api/v1/internal/delivery-partners/{p}", customer)
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void thePartnersDeliveryServiceKeptAreTakenOverOnce() throws Exception {
        long existing = readyPartner("Ravi Kumar");
        long suspended = randomId();
        long working = randomId();
        // Registered here before the import (their first call), never changed since
        long registeredMeanwhile = knownPartner("Kavya Nair");
        asPartner(registeredMeanwhile, get("/api/v1/delivery/partners/me"), null).andExpect(jsonPath("$.version").value(0));
        when(deliveryServiceClient.findPartners(0, 100)).thenReturn(new LegacyPartnerPage(List.of(
                legacy(existing, "Somebody Else", "SCOOTER", "AVAILABLE", false, null, null, 9),
                legacy(suspended, "Anil Das", "BICYCLE", "SUSPENDED", true, "Fraud check", null, 4),
                legacy(registeredMeanwhile, "Kavya Nair", "CAR", "SUSPENDED", true, "Licence expired", null, 30)), false));
        when(deliveryServiceClient.findPartners(1, 100)).thenReturn(new LegacyPartnerPage(List.of(
                legacy(working, "Meena Rao", "HOVERCRAFT", "BUSY", false, null, 812L, 12)), true));

        mockMvc.perform(post("/api/v1/internal/delivery-partners/import").with(TestTokens.serviceToken()))
                .andExpect(jsonPath("$.read").value(4))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.skipped").value(1));

        // Changed here since (profile filled in): left as it is
        asAdmin(get("/api/v1/admin/delivery-partners/{p}", existing), null)
                .andExpect(jsonPath("$.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"));
        // Registered here but never changed: completed from delivery-service, and delivery-service is told
        asAdmin(get("/api/v1/admin/delivery-partners/{p}", registeredMeanwhile), null)
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Licence expired"))
                .andExpect(jsonPath("$.vehicleType").value("CAR"))
                .andExpect(jsonPath("$.completedDeliveries").value(30));
        List<DeliveryPartnerChanged> completed = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.DELIVERY_PARTNER_CHANGED, String.valueOf(registeredMeanwhile), DeliveryPartnerChanged.class, 2);
        assertThat(completed.getLast().getChangeType()).isEqualTo("IMPORTED");
        assertThat(completed.getLast().getVersion()).isEqualTo(1L);
        assertThat(completed.getLast().getAccountStatus()).isEqualTo("SUSPENDED");
        asAdmin(get("/api/v1/admin/delivery-partners/{p}", suspended), null)
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Fraud check"))
                .andExpect(jsonPath("$.vehicleType").value("BICYCLE"));
        asAdmin(get("/api/v1/admin/delivery-partners/{p}", working), null)
                .andExpect(jsonPath("$.status").value("BUSY"))
                .andExpect(jsonPath("$.currentDeliveryId").value(812))
                .andExpect(jsonPath("$.completedDeliveries").value(12))
                .andExpect(jsonPath("$.vehicleType").doesNotExist());
        // The copy continues from delivery-service's version: an older report is ignored, a newer one applies
        publish(Topics.DELIVERY_PARTNER_STATUS_CHANGED, working, workStatus(working, 12, "AVAILABLE", null, 13));
        await().atMost(TIMEOUT).until(() -> partner(working).getStatus() == PartnerStatus.AVAILABLE);

        when(deliveryServiceClient.findPartners(anyInt(), anyInt())).thenReturn(new LegacyPartnerPage(List.of(
                legacy(working, "Meena Rao", "CAR", "OFFLINE", false, null, null, 0)), true));
        mockMvc.perform(post("/api/v1/internal/delivery-partners/import").with(TestTokens.serviceToken()))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
        assertThat(partner(working).getCompletedDeliveries()).isEqualTo(13);
    }

    @Test
    void auditRecordsCanBeSentAgainToThePlatformWideAuditLog() throws Exception {
        mockMvc.perform(post("/api/v1/internal/audit-logs/events/republish").with(TestTokens.serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("delivery-partner-service"))
                .andExpect(jsonPath("$.republished").isNumber());
    }

    /** A partner user-service knows; registered here on their first call. */
    private long knownPartner(String name) {
        long partner = randomId();
        users.put(partner, new UserSummary(partner, name, partner + "@example.com", "+919800000000",
                "DELIVERY_PARTNER", "ACTIVE"));
        return partner;
    }

    /** A registered partner with vehicle and city (this test's city). */
    private long readyPartner(String name) throws Exception {
        long partner = knownPartner(name);
        asPartner(partner, put("/api/v1/delivery/partners/me"),
                "{\"vehicleType\": \"MOTORCYCLE\", \"vehicleNumber\": \"KA01AB1234\", \"city\": \"" + city + "\"}")
                .andExpect(status().isOk());
        return partner;
    }

    private DeliveryPartner partner(long userId) {
        return deliveryPartnerRepository.findById(userId).orElseThrow();
    }

    private LegacyPartner legacy(long userId, String name, String vehicleType, String status, boolean suspended,
                                 String suspensionReason, Long currentDeliveryId, int completed) {
        return new LegacyPartner(userId, name, "+919800000002", vehicleType, "KA05XY9999", city, status,
                suspended ? suspensionReason : null, suspended, suspensionReason, currentDeliveryId,
                Instant.parse("2026-10-01T09:00:00Z"), completed, 11L);
    }

    private static UserRegistered userRegistered(long userId, String name, String role) {
        return UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setUserId(userId).setFullName(name).setEmail(userId + "@example.com").setPhone("+919800000001")
                .setRole(role).build();
    }

    private static DeliveryPartnerStatusChanged workStatus(long partner, long version, String status,
                                                           Long currentDeliveryId, int completed) {
        return DeliveryPartnerStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setPartnerUserId(partner).setVersion(version).setStatus(status)
                .setCurrentDeliveryId(currentDeliveryId)
                .setAvailableSince("AVAILABLE".equals(status) ? Instant.now() : null)
                .setCompletedDeliveries(completed).build();
    }

    private void publish(String topic, long key, SpecificRecord event) throws Exception {
        kafkaTemplate.send(topic, String.valueOf(key), event).get();
    }

    private ResultActions asPartner(long partner, MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.with(TestTokens.as(partner, "DELIVERY_PARTNER")), json);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.with(TestTokens.as(ADMIN, "PLATFORM_ADMIN")), json);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String json) throws Exception {
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    }
}
