package com.fooddelivery.deliveryservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.deliveryservice.client.DeliveryPartnerServiceClient;
import com.fooddelivery.deliveryservice.client.DeliveryPartnerServiceClient.PartnerProfile;
import com.fooddelivery.deliveryservice.client.OrderServiceClient;
import com.fooddelivery.deliveryservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.deliveryservice.client.RemoteRecords.RemoteAddress;
import com.fooddelivery.deliveryservice.client.RestaurantServiceClient;
import com.fooddelivery.deliveryservice.client.RestaurantServiceClient.RestaurantSummary;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.exception.RemoteServiceException;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.OrderDetailsRepository;
import com.fooddelivery.deliveryservice.repository.PickupPointRepository;
import com.fooddelivery.deliveryservice.repository.ProcessedEventRepository;
import com.fooddelivery.deliveryservice.repository.RiderLocationRepository;
import com.fooddelivery.deliveryservice.service.DeliveryAssignmentService;
import com.fooddelivery.deliveryservice.service.RiderLocationService;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.delivery.DeliveryPartnerStatusChanged;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderDeliveryAddress;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.partner.DeliveryPartnerChanged;
import com.fooddelivery.events.restaurant.RestaurantAddress;
import com.fooddelivery.events.restaurant.RestaurantChanged;
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
 * Full application against a real MySQL and Kafka (Testcontainers). delivery-partner-, restaurant- and order-service
 * are mocked; their events are published to Kafka by the tests. Each test works in its own (random) city, so the
 * partners of one test are never offered another test's deliveries. The assignment job is run by the tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DeliveryServiceIntegrationTest {

    private static final long ADMIN = 1L;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    /** Partners as delivery-partner-service would report them (asked when an event has not arrived yet). */
    private final Map<Long, PartnerProfile> partners = new ConcurrentHashMap<>();
    private volatile boolean orderServiceDown;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private DeliveryPartnerRepository deliveryPartnerRepository;
    @Autowired
    private OrderDetailsRepository orderDetailsRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private PickupPointRepository pickupPointRepository;
    @Autowired
    private DeliveryAssignmentService deliveryAssignmentService;
    @Autowired
    private RiderLocationService riderLocationService;
    @Autowired
    private RiderLocationRepository riderLocationRepository;

    @MockitoBean
    private DeliveryPartnerServiceClient deliveryPartnerServiceClient;
    @MockitoBean
    private RestaurantServiceClient restaurantServiceClient;
    @MockitoBean
    private OrderServiceClient orderServiceClient;

    private String city;

    @BeforeEach
    void setUp() {
        city = "City-" + UUID.randomUUID().toString().substring(0, 8);
        when(deliveryPartnerServiceClient.findPartner(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(partners.get(invocation.<Long>getArgument(0))));
        when(orderServiceClient.findOrder(anyLong())).thenAnswer(invocation -> {
            if (orderServiceDown) {
                throw new RemoteServiceException("order-service", new RuntimeException("Connection refused"));
            }
            long orderId = invocation.getArgument(0);
            return Optional.of(new OrderSummary(orderId, 7L, 10L, "READY_FOR_PICKUP", "UPI",
                    new BigDecimal("353.95"), "INR", "Spice Hub", "Priya Sharma", "+919876543210",
                    new RemoteAddress("12 MG Road", null, city, "Karnataka", "560001", "India", null, null)));
        });
        when(restaurantServiceClient.findRestaurant(anyLong())).thenAnswer(invocation -> {
            long restaurantId = invocation.getArgument(0);
            return Optional.of(new RestaurantSummary(restaurantId, 3L, "Spice Hub", 3L,
                    new RemoteAddress("45 Brigade Road", null, city, "Karnataka", "560025", "India", null, null)));
        });
    }

    @Test
    void aPartnerFromDeliveryPartnerServiceGoesOnlineOnceTheProfileIsComplete() throws Exception {
        long partner = randomId();
        publish(Topics.DELIVERY_PARTNER_CHANGED, partner, partnerChanged(partner, "REGISTERED", 0, null, null));
        await().atMost(TIMEOUT).until(() -> deliveryPartnerRepository.existsById(partner));
        assertThat(partner(partner).getStatus()).isEqualTo(PartnerStatus.OFFLINE);
        assertThat(partner(partner).getFullName()).isEqualTo("Ravi Kumar");

        // No vehicle and city yet, here or in delivery-partner-service
        partners.put(partner, profile(partner, "Ravi Kumar", null, "ACTIVE", null, 0));
        asPartner(partner, put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"AVAILABLE\"}")
                .andExpect(status().isUnprocessableContent());

        // Filled in a moment ago, the event still on its way: delivery-partner-service is asked
        partners.put(partner, profile(partner, "Ravi Kumar", city, "ACTIVE", null, 1));
        asPartner(partner, put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"AVAILABLE\"}")
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.vehicleNumber").value("KA01AB1234"))
                .andExpect(jsonPath("$.readyToWork").value(true));
        asPartner(partner, put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"BUSY\"}")
                .andExpect(status().isBadRequest());

        // The late event (same version) and an older one change nothing; a newer one does
        DeliveryPartnerChanged late = partnerChanged(partner, "PROFILE_UPDATED", 1, "Elsewhere", null);
        publish(Topics.DELIVERY_PARTNER_CHANGED, partner, late);
        publish(Topics.DELIVERY_PARTNER_CHANGED, partner, partnerChanged(partner, "REGISTERED", 0, null, null));
        DeliveryPartnerChanged renamed = partnerChanged(partner, "PROFILE_UPDATED", 2, city, null);
        renamed.setFullName("Ravi K.");
        publish(Topics.DELIVERY_PARTNER_CHANGED, partner, renamed);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(renamed.getEventId()));
        assertThat(partner(partner).getCity()).isEqualTo(city);
        assertThat(partner(partner).getFullName()).isEqualTo("Ravi K.");
        assertThat(partner(partner).getStatus()).isEqualTo(PartnerStatus.AVAILABLE);

        // delivery-partner-service learns that the partner is online
        DeliveryPartnerStatusChanged online = KafkaTestEvents.awaitEvent(consumerFactory,
                Topics.DELIVERY_PARTNER_STATUS_CHANGED, String.valueOf(partner), DeliveryPartnerStatusChanged.class);
        assertThat(online.getStatus()).isEqualTo("AVAILABLE");
        assertThat(online.getAvailableSince()).isNotNull();
        assertThat(online.getVersion()).isPositive();

        // An account that is not a delivery partner, and the partner owner being down (503, never a guess)
        asPartner(randomId(), put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"AVAILABLE\"}")
                .andExpect(status().isNotFound());
        long unknown = randomId();
        when(deliveryPartnerServiceClient.findPartner(unknown))
                .thenThrow(new RemoteServiceException("delivery-partner-service", new RuntimeException("Connection refused")));
        asPartner(unknown, put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"OFFLINE\"}")
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void aCashOrderIsAssignedPickedUpAndDeliveredAndEveryStepIsPublished() throws Exception {
        long partner = onlinePartner("Ravi Kumar");
        long customer = randomId();
        long orderId = randomId();
        long restaurantId = randomId();
        long deliveryId = readyOrder(orderId, customer, restaurantId, "CASH_ON_DELIVERY");

        assertThat(deliveryAssignmentService.assignWaitingDeliveries()).isPositive();

        // The partner sees the offer with everything needed to do the job
        asPartner(partner, get("/api/v1/delivery/deliveries/mine/current"), null)
                .andExpect(jsonPath("$.id").value(deliveryId))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.pickupAddress.line1").value("45 Brigade Road"))
                .andExpect(jsonPath("$.dropoffAddress.line1").value("12 MG Road"))
                .andExpect(jsonPath("$.customerPhone").value("+919876543210"))
                .andExpect(jsonPath("$.cashToCollect").value(353.95));
        // Nothing to show the customer before the partner accepts
        asCustomer(customer, get("/api/v1/delivery/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.partnerName").doesNotExist());

        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/pickup", deliveryId), null)
                .andExpect(status().isConflict());
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/accept", deliveryId), null)
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        asCustomer(customer, get("/api/v1/delivery/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.partnerName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.partnerPhone").value("+919800000000"))
                .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"));
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/pickup", deliveryId), null)
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        // Cash on delivery: the exact amount must be confirmed
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/deliver", deliveryId), "{}")
                .andExpect(status().isUnprocessableContent());
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/deliver", deliveryId), "{\"cashCollected\": 353.95}")
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.cashCollected").value(353.95));

        // The partner is free again and can take the next delivery
        assertThat(partner(partner).getStatus()).isEqualTo(PartnerStatus.AVAILABLE);
        assertThat(partner(partner).getCompletedDeliveries()).isEqualTo(1);
        assertThat(partner(partner).getCurrentDeliveryId()).isNull();
        asPartner(partner, get("/api/v1/delivery/deliveries/mine/current"), null).andExpect(status().isNoContent());
        asPartner(partner, get("/api/v1/delivery/deliveries/mine").param("status", "DELIVERED"), null)
                .andExpect(jsonPath("$.totalElements").value(1));
        asAdmin(get("/api/v1/admin/delivery-partners/{p}/deliveries", partner), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"));

        // delivery-partner-service followed every change: online, busy, free again with one delivery more
        List<DeliveryPartnerStatusChanged> partnerEvents = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.DELIVERY_PARTNER_STATUS_CHANGED, String.valueOf(partner), DeliveryPartnerStatusChanged.class, 3);
        assertThat(partnerEvents).extracting(DeliveryPartnerStatusChanged::getStatus)
                .containsExactly("AVAILABLE", "BUSY", "AVAILABLE");
        assertThat(partnerEvents.get(1).getCurrentDeliveryId()).isEqualTo(deliveryId);
        assertThat(partnerEvents.get(2).getCompletedDeliveries()).isEqualTo(1);
        assertThat(partnerEvents).extracting(DeliveryPartnerStatusChanged::getVersion).isSorted();

        // Every step went to Kafka for order-service and notification-service (outbox -> relay)
        List<DeliveryStatusChanged> events = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.DELIVERY_STATUS_CHANGED, String.valueOf(orderId), DeliveryStatusChanged.class, 5);
        assertThat(events).extracting(DeliveryStatusChanged::getStatus)
                .containsExactly("PENDING", "ASSIGNED", "ACCEPTED", "PICKED_UP", "DELIVERED");
        assertThat(events.get(2).getPartnerName()).isEqualTo("Ravi Kumar");
        assertThat(events.get(2).getVehicleType()).isEqualTo("MOTORCYCLE");
        assertThat(events.getLast().getCashCollected()).isEqualByComparingTo("353.95");
        assertThat(events.getLast().getCustomerId()).isEqualTo(customer);

        // The customer follows the milestones; another customer sees nothing
        asCustomer(customer, get("/api/v1/delivery/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.partnerPhone").doesNotExist())
                .andExpect(jsonPath("$.steps[*].status").value(org.hamcrest.Matchers.contains(
                        "PENDING", "ACCEPTED", "PICKED_UP", "DELIVERED")));
        asCustomer(randomId(), get("/api/v1/delivery/orders/{o}", orderId), null).andExpect(status().isNotFound());

        // Admins find it and see it in today's report
        asAdmin(get("/api/v1/admin/deliveries").param("orderId", String.valueOf(orderId)), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].partnerName").value("Ravi Kumar"));
        asAdmin(get("/api/v1/admin/deliveries/summary"), null)
                .andExpect(jsonPath("$.delivered").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.averageDeliveryMinutes").isNumber());
        // ... and among today's partners (for the dashboard and the delivery report)
        asAdmin(get("/api/v1/admin/delivery-partners/stats").param("limit", "100"), null)
                .andExpect(jsonPath("$.topPartners[?(@.userId == " + partner + ")].delivered")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.topPartners[?(@.userId == " + partner + ")].offersMissed")
                        .value(org.hamcrest.Matchers.contains(0)))
                .andExpect(jsonPath("$.online").isNumber())
                .andExpect(jsonPath("$.waitingForPartner").isNumber());
    }

    @Test
    void thePartnersLivePositionReachesOnlyTheirCustomerWhileTheDeliveryIsOnItsWay() throws Exception {
        long partner = onlinePartner("Ravi Kumar");
        long customer = randomId();
        long orderId = randomId();
        long deliveryId = readyOrder(orderId, customer, randomId(), "UPI");
        deliveryAssignmentService.assignWaitingDeliveries();
        String position = "{\"latitude\": 12.971599, \"longitude\": 77.594566, \"accuracyMeters\": 12, \"headingDeg\": 90}";

        // An offer is not on its way yet: no position accepted, no live map
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/location", deliveryId), position)
                .andExpect(status().isConflict());
        asCustomer(customer, get("/api/v1/delivery/orders/{o}/live", orderId), null).andExpect(status().isNotFound());

        // Accepted: the map is there, without a position until the partner's phone sends one
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/accept", deliveryId), null).andExpect(status().isOk());
        asCustomer(customer, get("/api/v1/delivery/orders/{o}/live", orderId), null)
                .andExpect(jsonPath("$.source").value("GPS"))
                .andExpect(jsonPath("$.phase").value("TO_RESTAURANT"))
                .andExpect(jsonPath("$.rider").doesNotExist())
                .andExpect(jsonPath("$.partner.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$.partner.phone").value("+919800000000"))
                .andExpect(jsonPath("$.dropoff.address").value("12 MG Road, " + city))
                .andExpect(jsonPath("$.dropoff.location.lat").value(12.9756))
                .andExpect(jsonPath("$.dropoff.location.lng").value(77.6066))
                // The restaurant's address has no map coordinates: no invented pin
                .andExpect(jsonPath("$.restaurant.name").value("Spice Hub"))
                .andExpect(jsonPath("$.restaurant.location").doesNotExist());

        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/location", deliveryId), position)
                .andExpect(status().isNoContent());
        asCustomer(customer, get("/api/v1/delivery/orders/{o}/live", orderId), null)
                .andExpect(jsonPath("$.rider.location.lat").value(12.971599))
                .andExpect(jsonPath("$.rider.location.lng").value(77.594566))
                .andExpect(jsonPath("$.rider.headingDeg").value(90))
                .andExpect(jsonPath("$.rider.accuracyMeters").value(12))
                .andExpect(jsonPath("$.rider.updatedAt").isNotEmpty());

        // Nobody else: another customer, another partner, impossible coordinates
        asCustomer(randomId(), get("/api/v1/delivery/orders/{o}/live", orderId), null).andExpect(status().isNotFound());
        asPartner(randomId(), put("/api/v1/delivery/deliveries/mine/{d}/location", deliveryId), position)
                .andExpect(status().isNotFound());
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/location", deliveryId),
                "{\"latitude\": 95.0, \"longitude\": 77.5}").andExpect(status().isBadRequest());

        // Picked up: on the way to the customer, the position still shown
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/pickup", deliveryId), null).andExpect(status().isOk());
        asCustomer(customer, get("/api/v1/delivery/orders/{o}/live", orderId), null)
                .andExpect(jsonPath("$.phase").value("TO_CUSTOMER"))
                .andExpect(jsonPath("$.rider.location.lat").value(12.971599));

        // Delivered: no position any more (it is deleted), no phone number
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/deliver", deliveryId), null).andExpect(status().isOk());
        asCustomer(customer, get("/api/v1/delivery/orders/{o}/live", orderId), null)
                .andExpect(jsonPath("$.phase").value("DELIVERED"))
                .andExpect(jsonPath("$.rider").doesNotExist())
                .andExpect(jsonPath("$.partner.phone").doesNotExist())
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/location", deliveryId), position)
                .andExpect(status().isConflict());
        riderLocationService.deleteFinished();
        assertThat(riderLocationRepository.existsById(deliveryId)).isFalse();
    }

    @Test
    void aDeclinedOfferGoesToTheNextPartnerAndAnUnansweredOneTimesOut() throws Exception {
        long first = onlinePartner("Ravi Kumar");
        long second = onlinePartner("Anil Das");
        long orderId = randomId();
        long deliveryId = readyOrder(orderId, randomId(), randomId(), "UPI");

        // The partner who has waited longest gets it first
        deliveryAssignmentService.assignWaitingDeliveries();
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getPartnerUserId()).isEqualTo(first);
        asPartner(first, put("/api/v1/delivery/deliveries/mine/{d}/decline", deliveryId), "{\"reason\": \"Flat tyre\"}")
                .andExpect(status().isNoContent());
        assertThat(partner(first).getStatus()).isEqualTo(PartnerStatus.AVAILABLE);

        // Offered to the next one, never again to the partner who declined
        deliveryAssignmentService.assignWaitingDeliveries();
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getPartnerUserId()).isEqualTo(second);

        // The second partner does not answer (accept timeout is 2s in the tests): offer taken back, partner offline
        await().atMost(TIMEOUT).until(() -> {
            deliveryAssignmentService.expireUnansweredOffers();
            return deliveryRepository.findById(deliveryId).orElseThrow().getStatus() == DeliveryStatus.PENDING;
        });
        assertThat(partner(second).getStatus()).isEqualTo(PartnerStatus.OFFLINE);
        deliveryAssignmentService.assignWaitingDeliveries();
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.PENDING);

        // An admin decides: the first partner takes it after all (audited)
        asAdmin(put("/api/v1/admin/deliveries/{d}/reassign", deliveryId),
                "{\"partnerUserId\": " + first + ", \"reason\": \"Tyre fixed, partner confirmed by phone\"}")
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.partnerUserId").value(first))
                .andExpect(jsonPath("$.timeline[*].event").value(org.hamcrest.Matchers.contains(
                        "CREATED", "ASSIGNED", "DECLINED", "ASSIGNED", "EXPIRED", "REASSIGNED")));
        asAdmin(get("/api/v1/admin/deliveries/{d}/audit-logs", deliveryId), null)
                .andExpect(jsonPath("$.content[0].action").value("DELIVERY_REASSIGNED"))
                .andExpect(jsonPath("$.content[0].newValue").value("partner " + first));
    }

    @Test
    void aSuspensionTakesBackAnOfferNotAcceptedYet() throws Exception {
        long partner = onlinePartner("Ravi Kumar");
        long deliveryId = readyOrder(randomId(), randomId(), randomId(), "CARD");
        deliveryAssignmentService.assignWaitingDeliveries();
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);

        // Suspended in delivery-partner-service while the offer was on its way to the partner
        publish(Topics.DELIVERY_PARTNER_CHANGED, partner, partnerChanged(partner, "SUSPENDED", 2, city, "Fraud check"));
        await().atMost(TIMEOUT).until(() -> partner(partner).getStatus() == PartnerStatus.SUSPENDED);
        assertThat(partner(partner).getStatusReason()).isEqualTo("Fraud check");
        assertThat(partner(partner).getCurrentDeliveryId()).isNull();
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getPartnerUserId()).isNull();
        asAdmin(get("/api/v1/admin/deliveries/{d}", deliveryId), null)
                .andExpect(jsonPath("$.timeline[-1].event").value("REASSIGNED"))
                .andExpect(jsonPath("$.timeline[-1].note").value("Taken back: Ravi Kumar was suspended"));
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/accept", deliveryId), null)
                .andExpect(status().isNotFound());
        asPartner(partner, put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"AVAILABLE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Your account is suspended: Fraud check. Please contact support."));
        asAdmin(get("/api/v1/admin/delivery-partners/availability"), null)
                .andExpect(jsonPath("$.byStatus.SUSPENDED").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // The next partner of the city gets it; the suspended one never again
        long next = onlinePartner("Anil Das");
        deliveryAssignmentService.assignWaitingDeliveries();
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getPartnerUserId()).isEqualTo(next);

        // Reactivated: offline, and online again when they choose
        publish(Topics.DELIVERY_PARTNER_CHANGED, partner, partnerChanged(partner, "ACTIVATED", 3, city, null));
        await().atMost(TIMEOUT).until(() -> partner(partner).getStatus() == PartnerStatus.OFFLINE);
        asPartner(partner, put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"AVAILABLE\"}")
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        DeliveryPartnerStatusChanged last = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.DELIVERY_PARTNER_STATUS_CHANGED, String.valueOf(partner), DeliveryPartnerStatusChanged.class, 5)
                .getLast();
        assertThat(last.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void aPartnerSuspendedDuringAnAcceptedDeliveryFinishesItFirst() throws Exception {
        long partner = onlinePartner("Ravi Kumar");
        long deliveryId = readyOrder(randomId(), randomId(), randomId(), "UPI");
        deliveryAssignmentService.assignWaitingDeliveries();
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/accept", deliveryId), null).andExpect(status().isOk());

        DeliveryPartnerChanged suspended = partnerChanged(partner, "SUSPENDED", 2, city, "Documents expired");
        publish(Topics.DELIVERY_PARTNER_CHANGED, partner, suspended);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(suspended.getEventId()));
        assertThat(partner(partner).getStatus()).isEqualTo(PartnerStatus.BUSY);
        assertThat(partner(partner).isSuspended()).isTrue();

        // The food reaches the customer; then the partner is suspended instead of free
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/pickup", deliveryId), null).andExpect(status().isOk());
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/deliver", deliveryId), null)
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(partner(partner).getStatus()).isEqualTo(PartnerStatus.SUSPENDED);
        assertThat(partner(partner).getStatusReason()).isEqualTo("Documents expired");
        assertThat(partner(partner).getCompletedDeliveries()).isEqualTo(1);
        assertThat(partner(partner).getAvailableSince()).isNull();

        // delivery-partner-service reads the whole list once (taking over partners kept here before it existed)
        mockMvc.perform(get("/api/v1/internal/delivery-partners").with(TestTokens.serviceToken()).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.userId == " + partner + ")].suspended")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.content[?(@.userId == " + partner + ")].suspensionReason")
                        .value(org.hamcrest.Matchers.contains("Documents expired")));
        mockMvc.perform(get("/api/v1/internal/delivery-partners")
                        .with(TestTokens.serviceToken())
                        .param("size", "501"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancellingTheOrderCancelsTheDeliveryAndFreesThePartner() throws Exception {
        long partner = onlinePartner("Ravi Kumar");
        long orderId = randomId();
        long deliveryId = readyOrder(orderId, randomId(), randomId(), "UPI");
        deliveryAssignmentService.assignWaitingDeliveries();
        asPartner(partner, put("/api/v1/delivery/deliveries/mine/{d}/accept", deliveryId), null).andExpect(status().isOk());

        publish(Topics.ORDER_CANCELLED, orderId, orderCancelled(orderId, "ACCEPTED"));

        await().atMost(TIMEOUT).until(() ->
                deliveryRepository.findById(deliveryId).orElseThrow().getStatus() == DeliveryStatus.CANCELLED);
        assertThat(partner(partner).getStatus()).isEqualTo(PartnerStatus.AVAILABLE);
        DeliveryStatusChanged last = KafkaTestEvents.awaitEvents(consumerFactory, Topics.DELIVERY_STATUS_CHANGED,
                String.valueOf(orderId), DeliveryStatusChanged.class, 4).getLast();
        assertThat(last.getStatus()).isEqualTo("CANCELLED");
        assertThat(last.getPreviousStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void anOrderCancelledBeforeItsReadyEventArrivesNeverGetsAPartner() throws Exception {
        onlinePartner("Ravi Kumar");
        long orderId = randomId();
        long restaurantId = randomId();
        long customer = randomId();
        // OrderCancelled and OrderStatusChanged are different topics: here the cancellation is read first
        publish(Topics.ORDER_CANCELLED, orderId, orderCancelled(orderId, "READY_FOR_PICKUP"));
        await().atMost(TIMEOUT).until(() -> deliveryRepository.findByOrderId(orderId).isPresent());

        OrderStatusChanged ready = readyForPickup(orderId, customer, restaurantId);
        publish(Topics.ORDER_STATUS_CHANGED, orderId, ready);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(ready.getEventId()));
        deliveryAssignmentService.runAssignmentRound();

        Delivery delivery = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(delivery.getPartnerUserId()).isNull();
    }

    @Test
    void detailsThatNeverArrivedAsEventsAreFetchedFromTheOwningServices() throws Exception {
        long partner = onlinePartner("Ravi Kumar");
        long orderId = randomId();
        long restaurantId = randomId();
        // Only "food ready": no OrderCreated, no RestaurantChanged (for example published before this service existed)
        publish(Topics.ORDER_STATUS_CHANGED, orderId, readyForPickup(orderId, 7L, restaurantId));
        await().atMost(TIMEOUT).until(() -> deliveryRepository.findByOrderId(orderId).isPresent());
        assertThat(deliveryRepository.findByOrderId(orderId).orElseThrow().hasAllDetails()).isFalse();

        // order-service is down: the delivery waits, nothing is lost
        orderServiceDown = true;
        deliveryAssignmentService.runAssignmentRound();
        assertThat(deliveryRepository.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.PENDING);

        // Back up: the next run completes the delivery and offers it
        orderServiceDown = false;
        deliveryAssignmentService.runAssignmentRound();
        Delivery delivery = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(delivery.getPartnerUserId()).isEqualTo(partner);
        assertThat(delivery.getDropoffAddress().line1()).isEqualTo("12 MG Road");
        assertThat(delivery.getPickupAddress().line1()).isEqualTo("45 Brigade Road");
        assertThat(orderDetailsRepository.existsById(orderId)).isTrue();
        assertThat(pickupPointRepository.existsById(restaurantId)).isTrue();
    }

    /**
     * A partner with a complete profile, online in this test's city. Their DeliveryPartnerChanged event has not
     * arrived, so going online asks delivery-partner-service.
     */
    private long onlinePartner(String name) throws Exception {
        long partner = randomId();
        partners.put(partner, profile(partner, name, city, "ACTIVE", null, 1));
        asPartner(partner, put("/api/v1/delivery/partners/me/availability"), "{\"status\": \"AVAILABLE\"}")
                .andExpect(status().isOk());
        return partner;
    }

    /** A partner as delivery-partner-service reports them; without a city they have no vehicle either. */
    private static PartnerProfile profile(long partner, String name, String partnerCity, String accountStatus,
                                          String suspensionReason, long version) {
        return new PartnerProfile(partner, name, "+919800000000", partnerCity == null ? null : "MOTORCYCLE",
                partnerCity == null ? null : "KA01AB1234", partnerCity, accountStatus, suspensionReason, version);
    }

    /** What delivery-partner-service publishes; a suspension reason means the account is suspended. */
    private static DeliveryPartnerChanged partnerChanged(long partner, String changeType, long version, String partnerCity,
                                                         String suspensionReason) {
        return DeliveryPartnerChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType(changeType).setPartnerUserId(partner).setVersion(version)
                .setFullName("Ravi Kumar").setPhone("+919800000000")
                .setVehicleType(partnerCity == null ? null : "MOTORCYCLE")
                .setVehicleNumber(partnerCity == null ? null : "KA01AB1234").setCity(partnerCity)
                .setAccountStatus(suspensionReason == null ? "ACTIVE" : "SUSPENDED").setSuspensionReason(suspensionReason)
                .build();
    }

    /**
     * What order-service and restaurant-service publish up to "food ready": the order, the restaurant's address
     * and then READY_FOR_PICKUP. Returns the id of the PENDING delivery.
     */
    private long readyOrder(long orderId, long customer, long restaurantId, String paymentMethod) throws Exception {
        publish(Topics.RESTAURANT_CHANGED, restaurantId, RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType("PROFILE_UPDATED").setRestaurantId(restaurantId).setVersion(2L).setOwnerUserId(3L)
                .setName("Spice Hub").setCity(city).setStatus("ACTIVE")
                .setAddress(RestaurantAddress.newBuilder().setLine1("45 Brigade Road").setCity(city)
                        .setState("Karnataka").setPostalCode("560025").setCountry("India").build())
                .build());
        publish(Topics.ORDER_CREATED, orderId, OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customer).setRestaurantId(restaurantId)
                .setStatus("PLACED").setPaymentMethod(paymentMethod).setItems(List.of())
                .setItemsTotal(new BigDecimal("299.00")).setDeliveryFee(new BigDecimal("40.00"))
                .setTaxAmount(new BigDecimal("14.95")).setTotalAmount(new BigDecimal("353.95")).setCurrency("INR")
                .setRestaurantName("Spice Hub").setCustomerName("Priya Sharma").setCustomerPhone("+919876543210")
                .setDeliveryAddress(OrderDeliveryAddress.newBuilder().setLine1("12 MG Road").setCity(city)
                        .setState("Karnataka").setPostalCode("560001").setCountry("India").setLatitude(12.9756)
                        .setLongitude(77.6066).build())
                .build());
        await().atMost(TIMEOUT).until(() -> orderDetailsRepository.existsById(orderId)
                && pickupPointRepository.existsById(restaurantId));

        publish(Topics.ORDER_STATUS_CHANGED, orderId, readyForPickup(orderId, customer, restaurantId));
        await().atMost(TIMEOUT).until(() -> deliveryRepository.findByOrderId(orderId).isPresent());
        Delivery delivery = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.hasAllDetails()).isTrue();
        return delivery.getId();
    }

    private static OrderStatusChanged readyForPickup(long orderId, long customer, long restaurantId) {
        return OrderStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customer).setRestaurantId(restaurantId)
                .setRestaurantName("Spice Hub").setStatus("READY_FOR_PICKUP").setPreviousStatus("PREPARING")
                .setChangedBy("RESTAURANT").build();
    }

    private static OrderCancelled orderCancelled(long orderId, String previousStatus) {
        return OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(7L).setRestaurantId(10L)
                .setFinalStatus("CANCELLED").setPreviousStatus(previousStatus).setCancelledBy("ADMIN")
                .setReason("Customer unreachable").setPaymentMethod("UPI").setPaymentStatus("PAID")
                .setTotalAmount(new BigDecimal("353.95")).setCurrency("INR").build();
    }

    @Test
    void ownerSeesWhichPartnersDeliveredMostForTheirRestaurantOnly() throws Exception {
        long owner = 3L;
        long restaurant = randomId();
        long otherRestaurant = randomId();
        long unknownRestaurant = randomId();
        long downRestaurant = randomId();
        long ravi = randomId();
        long anil = randomId();
        long nameless = randomId();
        long elsewhere = randomId();
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.LocalDate day = java.time.LocalDate.parse("2018-05-10"); // years back: no other test writes here
        when(restaurantServiceClient.findRestaurant(restaurant)).thenReturn(Optional.of(
                new RestaurantSummary(restaurant, owner, "Spice Hub", 1L, null)));
        when(restaurantServiceClient.findRestaurant(otherRestaurant)).thenReturn(Optional.of(
                new RestaurantSummary(otherRestaurant, 999L, "Other Kitchen", 1L, null)));
        when(restaurantServiceClient.findRestaurant(unknownRestaurant)).thenReturn(Optional.empty());
        when(restaurantServiceClient.findRestaurant(downRestaurant))
                .thenThrow(new RemoteServiceException("restaurant-service", new RuntimeException("Connection refused")));
        // Anil's name is not on his deliveries, only in his partner record; the nameless one has no name anywhere
        deliveryPartnerRepository.save(DeliveryPartner.register(anil, "Anil Das", null));

        java.util.function.BiFunction<Integer, Integer, Instant> at = (hour, minute) ->
                day.atTime(hour, minute).atZone(zone).toInstant();
        DeliveryStatus delivered = DeliveryStatus.DELIVERED;
        String partnersOf = "/api/v1/restaurants/mine/{r}/deliveries/partners";

        List<Delivery> rows = new java.util.ArrayList<>();
        try {
            // ravi: 3 delivered in the day, 30 minutes on average (the first one just after local midnight, the last
            // in its last minute); anil: 2, 15 minutes; nameless: 2, 25 minutes (ranked after anil on speed)
            for (int minutes : new int[] {20, 30, 40}) {
                rows.add(delivery(restaurant, delivered, ravi, "Ravi Kumar",
                        at.apply(minutes == 20 ? 0 : minutes == 30 ? 12 : 23, minutes == 40 ? 59 : 0), minutes));
            }
            rows.add(delivery(restaurant, delivered, anil, null, at.apply(9, 0), 10));
            rows.add(delivery(restaurant, delivered, anil, null, at.apply(15, 0), 20));
            rows.add(delivery(restaurant, delivered, nameless, null, at.apply(10, 0), 20));
            rows.add(delivery(restaurant, delivered, nameless, null, at.apply(16, 0), 30));
            // not counted: cancelled, the minute before the day and the instant after it
            rows.add(delivery(restaurant, DeliveryStatus.CANCELLED, ravi, "Ravi Kumar", null, 10));
            rows.add(delivery(restaurant, delivered, nameless, null, day.atStartOfDay(zone).toInstant().minusSeconds(60), 10));
            rows.add(delivery(restaurant, delivered, nameless, null, day.plusDays(1).atStartOfDay(zone).toInstant(), 10));
            // another restaurant: ravi delivered a lot there, elsewhere only delivers for that restaurant
            for (int i = 0; i < 5; i++) {
                rows.add(delivery(otherRestaurant, delivered, ravi, "Ravi Kumar", at.apply(11, i), 15));
            }
            rows.add(delivery(otherRestaurant, delivered, elsewhere, "Other Rider", at.apply(11, 30), 15));

            asOwner(owner, get(partnersOf, restaurant)
                    .param("from", "2018-05-10").param("to", "2018-05-10"), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.restaurantId").value(restaurant))
                    .andExpect(jsonPath("$.from").value("2018-05-10")).andExpect(jsonPath("$.to").value("2018-05-10"))
                    .andExpect(jsonPath("$.partners.length()").value(3))
                    .andExpect(jsonPath("$.partners[0].partnerUserId").value(ravi))
                    .andExpect(jsonPath("$.partners[0].name").value("Ravi Kumar"))
                    .andExpect(jsonPath("$.partners[0].delivered").value(3))
                    .andExpect(jsonPath("$.partners[0].averageMinutes").value(30.0))
                    .andExpect(jsonPath("$.partners[1].partnerUserId").value(anil))
                    .andExpect(jsonPath("$.partners[1].name").value("Anil Das"))
                    .andExpect(jsonPath("$.partners[1].delivered").value(2))
                    .andExpect(jsonPath("$.partners[1].averageMinutes").value(15.0))
                    .andExpect(jsonPath("$.partners[2].partnerUserId").value(nameless))
                    .andExpect(jsonPath("$.partners[2].name").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.partners[2].averageMinutes").value(25.0));
            asOwner(owner, get(partnersOf, restaurant)
                    .param("from", "2018-05-10").param("to", "2018-05-10").param("limit", "1"), null)
                    .andExpect(jsonPath("$.partners.length()").value(1))
                    .andExpect(jsonPath("$.partners[0].partnerUserId").value(ravi));
            // the neighbouring days count once the range reaches them: nameless then has 4 and leads
            asOwner(owner, get(partnersOf, restaurant)
                    .param("from", "2018-05-09").param("to", "2018-05-11").param("limit", "1"), null)
                    .andExpect(jsonPath("$.partners[0].partnerUserId").value(nameless))
                    .andExpect(jsonPath("$.partners[0].delivered").value(4));
            // today by default: nobody delivered today
            asOwner(owner, get(partnersOf, restaurant), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.partners.length()").value(0));

            // Another owner's restaurant and an unknown one: 404 like the owner endpoints of order-service; the
            // restaurant-service being down is a 503, never a guess
            asOwner(owner, get(partnersOf, otherRestaurant), null).andExpect(status().isNotFound());
            asOwner(owner, get(partnersOf, unknownRestaurant), null).andExpect(status().isNotFound());
            asOwner(owner, get(partnersOf, downRestaurant), null)
                    .andExpect(status().isServiceUnavailable());

            asOwner(owner, get(partnersOf, restaurant)
                    .param("from", "2017-05-09").param("to", "2018-05-10"), null).andExpect(status().isBadRequest()); // 367 days
            asOwner(owner, get(partnersOf, restaurant).param("limit", "0"), null)
                    .andExpect(status().isBadRequest());
            asCustomer(randomId(), get(partnersOf, restaurant), null)
                    .andExpect(status().isForbidden());
            asAdmin(get(partnersOf, restaurant), null).andExpect(status().isForbidden());
            mockMvc.perform(get(partnersOf, restaurant)).andExpect(status().isUnauthorized());
        } finally {
            // Leave the shared database as it was: other tests count deliveries and partners
            deliveryRepository.deleteAllInBatch(rows);
            deliveryPartnerRepository.deleteById(anil);
        }
    }

    /** A delivery that was ready the given minutes before it was delivered (the API cannot back-date deliveries). */
    private Delivery delivery(long restaurantId, DeliveryStatus status, long partnerUserId, String partnerName,
                              Instant deliveredAt, int minutes) {
        Delivery delivery = new Delivery();
        delivery.setOrderId(randomId() * 1000 + ThreadLocalRandom.current().nextInt(1000));
        delivery.setRestaurantId(restaurantId);
        delivery.setStatus(status);
        delivery.setPartnerUserId(partnerUserId);
        delivery.setPartnerName(partnerName);
        delivery.setDeliveredAt(deliveredAt);
        delivery.setReadyAt(deliveredAt == null
                ? Instant.parse("2018-05-10T00:00:00Z") : deliveredAt.minusSeconds(minutes * 60L));
        return deliveryRepository.save(delivery);
    }

    private ResultActions asOwner(long owner, MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.with(TestTokens.as(owner, "RESTAURANT_ADMIN")), json);
    }

    private DeliveryPartner partner(long userId) {
        return deliveryPartnerRepository.findById(userId).orElseThrow();
    }

    private void publish(String topic, long key, SpecificRecord event) throws Exception {
        kafkaTemplate.send(topic, String.valueOf(key), event).get();
    }

    private ResultActions asPartner(long partner, MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.with(TestTokens.as(partner, "DELIVERY_PARTNER")), json);
    }

    private ResultActions asCustomer(long customer, MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.with(TestTokens.as(customer, "CUSTOMER")), json);
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
