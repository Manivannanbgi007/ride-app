package com.fooddelivery.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderPaymentRejected;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.orderservice.client.CatalogLookupClient;
import com.fooddelivery.orderservice.client.CatalogServiceClient;
import com.fooddelivery.orderservice.client.PromotionServiceClient;
import com.fooddelivery.orderservice.client.RemoteCalls;
import com.fooddelivery.orderservice.client.RemoteRecords.AddressSummary;
import com.fooddelivery.orderservice.client.RemoteRecords.CouponCheck;
import com.fooddelivery.orderservice.client.RemoteRecords.CouponReservation;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemCard;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemDetails;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemSummary;
import com.fooddelivery.orderservice.client.RemoteRecords.RestaurantSummary;
import com.fooddelivery.orderservice.client.RemoteRecords.UserSummary;
import com.fooddelivery.orderservice.client.RestaurantServiceClient;
import com.fooddelivery.orderservice.client.UserServiceClient;
import com.fooddelivery.orderservice.dto.request.PlaceOrderRequest;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.OutboxEvent;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OutboxEventRepository;
import com.fooddelivery.orderservice.service.CartService;
import com.fooddelivery.orderservice.service.OrderPaymentService;
import com.fooddelivery.orderservice.service.OrderPlacementService;
import com.fooddelivery.orderservice.service.UnpaidOrderExpiryService;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

/**
 * Full application against a real MySQL and Kafka (Testcontainers). The services order-service
 * calls (user, restaurant, catalog, promotion) are replaced by mocks that behave like a small live platform;
 * payment-service's events are published to Kafka by the tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    private static final long ADMIN = 1L;
    private static final long OWNER = 3L;
    private static final long BIRYANI = 100L;
    private static final long KULFI = 101L;
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(20);

    /** Menu prices as catalog-service would currently report them (tests can change them). */
    private final Map<Long, MenuItemSummary> catalog = new ConcurrentHashMap<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderPlacementService orderPlacementService;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private OrderPaymentService orderPaymentService;
    @Autowired
    private UnpaidOrderExpiryService unpaidOrderExpiryService;
    @Autowired
    private RemoteCalls remoteCalls;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private RetryRegistry retryRegistry;
    @Autowired
    private BulkheadRegistry bulkheadRegistry;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private RestaurantServiceClient restaurantServiceClient;
    @MockitoBean
    private CatalogServiceClient catalogServiceClient;
    @MockitoBean
    private CatalogLookupClient catalogLookupClient;
    @MockitoBean
    private PromotionServiceClient promotionServiceClient;

    private long restaurantId;

    /** Real access tokens (TestJwts) are checked with that helper's public key, as user-service's in production */
    @DynamicPropertySource
    static void tokensOfTheTests(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwts::jwksUri);
    }

    @BeforeEach
    void setUpPlatform() {
        restaurantId = randomId();
        catalog.put(BIRYANI, new MenuItemSummary(BIRYANI, restaurantId, "Chicken Biryani", new BigDecimal("299.00"),
                "NON_VEG", "ACTIVE", true));
        catalog.put(KULFI, new MenuItemSummary(KULFI, restaurantId, "Malai Kulfi", new BigDecimal("89.00"),
                "VEG", "ACTIVE", true));

        when(catalogServiceClient.findMenuItems(anyCollection())).thenAnswer(invocation -> {
            Map<Long, MenuItemSummary> found = new ConcurrentHashMap<>();
            for (Object id : invocation.<java.util.Collection<?>>getArgument(0)) {
                MenuItemSummary item = catalog.get((Long) id);
                if (item != null) {
                    found.put(item.id(), item);
                }
            }
            return found;
        });
        when(restaurantServiceClient.findRestaurant(restaurantId))
                .thenReturn(Optional.of(new RestaurantSummary(restaurantId, OWNER, "Spice Hub", "ACTIVE", true)));
        when(userServiceClient.findUser(anyLong())).thenAnswer(invocation -> Optional.of(new UserSummary(
                invocation.getArgument(0), "Priya Sharma", "priya@example.com", "+919876543210", "CUSTOMER", "ACTIVE")));
        when(userServiceClient.findAddress(anyLong(), anyLong())).thenAnswer(invocation -> Optional.of(new AddressSummary(
                invocation.getArgument(1), invocation.getArgument(0), "12 MG Road", null, "Bengaluru", "Karnataka",
                "560001", "India", null, null)));
    }

    @Test
    void fromCartToReadyForPickupWithIdempotentCheckout() throws Exception {
        long customer = randomId();

        // Cart: live prices and totals
        asCustomer(customer, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 2}").andExpect(status().isOk());
        asCustomer(customer, put("/api/v1/cart/items/{id}", KULFI), "{\"quantity\": 1}")
                .andExpect(jsonPath("$.price.itemsTotal").value(687.00))
                .andExpect(jsonPath("$.price.deliveryFee").value(0.00))
                .andExpect(jsonPath("$.orderable").value(true));

        // Checkout, then the same request again (a network retry)
        String key = "checkout-" + UUID.randomUUID();
        String body = placeOrder(customer, key, "CASH_ON_DELIVERY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.price.totalAmount").value(721.35))
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
        placeOrder(customer, key, "CASH_ON_DELIVERY")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(orderId));
        assertThat(orderRepository.findAll().stream().filter(order -> order.getCustomerId() == customer)).hasSize(1);

        // Same key, different request: refused
        placeOrder(customer, key, "CARD").andExpect(status().isUnprocessableContent());

        // The cart was emptied; a later price change does not touch the order
        asCustomer(customer, get("/api/v1/cart"), null).andExpect(jsonPath("$.items.length()").value(0));
        catalog.put(BIRYANI, new MenuItemSummary(BIRYANI, restaurantId, "Chicken Biryani", new BigDecimal("349.00"),
                "NON_VEG", "ACTIVE", true));

        // The restaurant works through the order
        asOwner(get("/api/v1/restaurants/mine/{r}/orders", restaurantId).param("status", "PLACED"), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                // Who ordered, so the restaurant can tell its orders apart in the list
                .andExpect(jsonPath("$.content[0].customerName").value("Priya Sharma"))
                // What was ordered, so the list can show the dishes
                .andExpect(jsonPath("$.content[0].items.length()").value(2))
                .andExpect(jsonPath("$.content[0].items[?(@.menuItemId == %s)].quantity", BIRYANI).value(2))
                .andExpect(jsonPath("$.content[0].items[?(@.name == 'Chicken Biryani')].foodType").value("NON_VEG"));
        asOwner(put("/api/v1/restaurants/mine/{r}/orders/{o}/accept", restaurantId, orderId), null)
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        asCustomer(customer, put("/api/v1/orders/{o}/cancel", orderId), "{\"reason\": \"Too slow\"}")
                .andExpect(status().isConflict());
        asOwner(put("/api/v1/restaurants/mine/{r}/orders/{o}/preparing", restaurantId, orderId), null).andExpect(status().isOk());
        asOwner(put("/api/v1/restaurants/mine/{r}/orders/{o}/ready", restaurantId, orderId), null)
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"));

        // Every status the order entered was published, in order (notifications, later delivery)
        List<OrderStatusChanged> statusEvents = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.ORDER_STATUS_CHANGED, String.valueOf(orderId), OrderStatusChanged.class, 4);
        assertThat(statusEvents).extracting(OrderStatusChanged::getStatus)
                .containsExactly("PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP");
        assertThat(statusEvents.getFirst().getPreviousStatus()).isNull();
        assertThat(statusEvents.get(1).getChangedBy()).isEqualTo("RESTAURANT");
        assertThat(statusEvents.get(1).getRestaurantName()).isEqualTo("Spice Hub");

        // Tracing across the outbox: each event row keeps the trace of the request that wrote it, and the relay
        // sends the event in that trace (the record's traceparent header continues it in the consumers)
        List<OutboxEvent> outboxRows = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId)
                .stream().filter(row -> row.getTopic().equals(Topics.ORDER_STATUS_CHANGED)).toList();
        assertThat(outboxRows).hasSize(4).allSatisfy(row ->
                assertThat(row.getTraceParent()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"));
        List<String> requestTraces = outboxRows.stream().map(row -> row.getTraceParent().substring(3, 35)).toList();
        assertThat(requestTraces).doesNotHaveDuplicates();
        List<String> sentTraces = KafkaTestEvents.awaitRecords(consumerFactory, Topics.ORDER_STATUS_CHANGED,
                        String.valueOf(orderId), OrderStatusChanged.class, 4).stream()
                .map(consumerRecord -> new String(consumerRecord.headers().lastHeader("traceparent").value(),
                        StandardCharsets.UTF_8).substring(3, 35))
                .toList();
        assertThat(sentTraces).isEqualTo(requestTraces);

        // The customer tracks it: copied prices and the full timeline
        asCustomer(customer, get("/api/v1/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.items[0].unitPrice").value(299.00))
                .andExpect(jsonPath("$.timeline[*].status").value(org.hamcrest.Matchers.contains(
                        "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP")))
                .andExpect(jsonPath("$.timeline[1].actorType").value("RESTAURANT"));

        // Sales summary for today
        asOwner(get("/api/v1/restaurants/mine/{r}/orders/sales-summary", restaurantId), null)
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.acceptedOrdersValue").value(721.35))
                // Today, day by day: the order placed (ready, not delivered yet)
                .andExpect(jsonPath("$.perDay.length()").value(1))
                .andExpect(jsonPath("$.perDay[0].orders").value(1))
                .andExpect(jsonPath("$.perDay[0].completed").value(0))
                // Hour by hour: all 24 hours, the order in exactly one of them
                .andExpect(jsonPath("$.perHour.length()").value(24))
                .andExpect(jsonPath("$.perHour[?(@.orders > 0)].orders").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void unpaidOnlineOrderIsHiddenFromTheRestaurantAndCanBeCancelledByTheCustomer() throws Exception {
        long customer = randomId();
        asCustomer(customer, put("/api/v1/cart/items/{id}", KULFI), "{\"quantity\": 1}").andExpect(status().isOk());

        String body = placeOrder(customer, "checkout-" + UUID.randomUUID(), "UPI")
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.price.deliveryFee").value(40.00))
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();

        asOwner(get("/api/v1/restaurants/mine/{r}/orders/{o}", restaurantId, orderId), null).andExpect(status().isNotFound());
        asCustomer(customer, put("/api/v1/orders/{o}/cancel", orderId), "{\"reason\": \"Payment app crashed\"}")
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("CUSTOMER"));
    }

    @Test
    void aCouponAppliedInTheCartIsReservedAtCheckoutAndGivenBackWhenTheOrderIsCancelled() throws Exception {
        long customer = randomId();
        long reservationId = randomId();
        asCustomer(customer, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 2}").andExpect(status().isOk());

        // A coupon the cart does not qualify for is refused with the reason
        when(promotionServiceClient.check(eq("BIGSPENDER"), eq(customer), eq(restaurantId), any())).thenReturn(
                new CouponCheck("BIGSPENDER", false, BigDecimal.ZERO, "Add items worth 402.00 more to use this coupon "
                        + "(minimum order 1000.00)", null));
        asCustomer(customer, put("/api/v1/cart/coupon"), "{\"code\": \"bigspender\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Add items worth 402.00 more to use this coupon (minimum order 1000.00)"));

        // One that it does: the discount comes off the items, before tax and the free-delivery threshold
        when(promotionServiceClient.check(eq("WELCOME100"), eq(customer), eq(restaurantId), any())).thenReturn(
                new CouponCheck("WELCOME100", true, new BigDecimal("100.00"), null, "100 off your first order"));
        asCustomer(customer, put("/api/v1/cart/coupon"), "{\"code\": \"welcome100\"}")
                .andExpect(jsonPath("$.coupon.code").value("WELCOME100"))
                .andExpect(jsonPath("$.coupon.applied").value(true))
                .andExpect(jsonPath("$.price.discountAmount").value(100.00))
                .andExpect(jsonPath("$.price.deliveryFee").value(40.00))
                .andExpect(jsonPath("$.price.totalAmount").value(562.90));

        String key = "checkout-" + UUID.randomUUID();
        when(promotionServiceClient.reserve(eq("checkout:" + customer + ":" + key), eq("WELCOME100"), eq(customer),
                eq(restaurantId), any()))
                .thenReturn(new CouponReservation(reservationId, "WELCOME100", new BigDecimal("100.00"), "RESERVED"));
        String body = placeOrder(customer, key, "CASH_ON_DELIVERY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponCode").value("WELCOME100"))
                .andExpect(jsonPath("$.price.discountAmount").value(100.00))
                .andExpect(jsonPath("$.price.totalAmount").value(562.90))
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
        asCustomer(customer, get("/api/v1/cart"), null).andExpect(jsonPath("$.coupon").isEmpty());

        // promotion-service learns which order the reservation became, and gets the use back on cancellation
        OrderCreated created = KafkaTestEvents.awaitEvents(consumerFactory, Topics.ORDER_CREATED, String.valueOf(orderId),
                OrderCreated.class, 1).getFirst();
        assertThat(created.getCouponCode()).isEqualTo("WELCOME100");
        assertThat(created.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(created.getCouponReservationId()).isEqualTo(reservationId);
        assertThat(created.getTotalAmount()).isEqualByComparingTo("562.90");

        asCustomer(customer, put("/api/v1/orders/{o}/cancel", orderId), "{\"reason\": \"Ordered by mistake\"}")
                .andExpect(status().isOk());
        OrderCancelled cancelled = KafkaTestEvents.awaitEvents(consumerFactory, Topics.ORDER_CANCELLED,
                String.valueOf(orderId), OrderCancelled.class, 1).getFirst();
        assertThat(cancelled.getCouponReservationId()).isEqualTo(reservationId);
    }

    @Test
    void adminStatisticsCountOrdersRevenueRestaurantsCustomersAndCities() throws Exception {
        String before = asAdmin(get("/api/v1/admin/orders/stats"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String citiesBefore = asAdmin(get("/api/v1/admin/orders/stats/locations"), null)
                .andReturn().getResponse().getContentAsString();

        // Asha: one order delivered (353.95), one cancelled; Bala: an online order still waiting for payment
        long asha = randomId();
        long bala = randomId();
        long partner = randomId();
        asCustomer(asha, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 1}").andExpect(status().isOk());
        long delivered = orderId(placeOrder(asha, "checkout-" + UUID.randomUUID(), "CASH_ON_DELIVERY"));
        for (String step : List.of("accept", "preparing", "ready")) {
            asOwner(put("/api/v1/restaurants/mine/{r}/orders/{o}/" + step, restaurantId, delivered), null)
                    .andExpect(status().isOk());
        }
        for (String step : List.of("ASSIGNED", "PICKED_UP", "DELIVERED")) {
            publish(deliveryEvent(delivered, asha, partner, step));
        }
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> asCustomer(asha, get("/api/v1/orders/{o}", delivered), null)
                .andExpect(jsonPath("$.status").value("DELIVERED")));
        asCustomer(asha, put("/api/v1/cart/items/{id}", KULFI), "{\"quantity\": 1}").andExpect(status().isOk());
        long cancelled = orderId(placeOrder(asha, "checkout-" + UUID.randomUUID(), "CASH_ON_DELIVERY"));
        asCustomer(asha, put("/api/v1/orders/{o}/cancel", cancelled), "{\"reason\": \"Changed my mind\"}")
                .andExpect(status().isOk());
        asCustomer(bala, put("/api/v1/cart/items/{id}", KULFI), "{\"quantity\": 2}").andExpect(status().isOk());
        placeOrder(bala, "checkout-" + UUID.randomUUID(), "UPI").andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));

        String after = asAdmin(get("/api/v1/admin/orders/stats"), null).andReturn().getResponse().getContentAsString();
        assertThat(delta(before, after, "$.orders")).isEqualTo(3);
        assertThat(delta(before, after, "$.completed")).isEqualTo(1);
        assertThat(delta(before, after, "$.cancelled")).isEqualTo(1);
        assertThat(delta(before, after, "$.inProgress")).isEqualTo(1);
        assertThat(delta(before, after, "$.revenue")).isEqualTo(353.95);
        assertThat(delta(before, after, "$.overall.revenue")).isEqualTo(353.95);
        assertThat(delta(before, after, "$.overall.inProgress")).isEqualTo(1);
        assertThat(delta(before, after, "$.perDay[-1].orders")).isEqualTo(3);
        assertThat(delta(before, after, "$.perDay[-1].revenue")).isEqualTo(353.95);
        assertThat(((Number) JsonPath.read(after, "$.byPaymentMethod.UPI.orders")).longValue()).isPositive();

        // The restaurant's figures leave out the unpaid order: it never reached the kitchen
        asAdmin(get("/api/v1/admin/orders/stats/restaurants").param("limit", "100"), null)
                .andExpect(jsonPath("$.topByRevenue[?(@.restaurantId == " + restaurantId + ")].orders")
                        .value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.topByRevenue[?(@.restaurantId == " + restaurantId + ")].revenue")
                        .value(org.hamcrest.Matchers.contains(353.95)))
                .andExpect(jsonPath("$.topByRevenue[?(@.restaurantId == " + restaurantId + ")].cancellationRate")
                        .value(org.hamcrest.Matchers.contains(50.0)));
        asAdmin(get("/api/v1/admin/orders/stats/customers").param("limit", "100"), null)
                .andExpect(jsonPath("$.topBySpend[?(@.customerId == " + asha + ")].spent")
                        .value(org.hamcrest.Matchers.contains(353.95)))
                .andExpect(jsonPath("$.topBySpend[?(@.customerId == " + asha + ")].orders")
                        .value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.orderFrequency['2-3']").isNumber());
        String citiesAfter = asAdmin(get("/api/v1/admin/orders/stats/locations"), null)
                .andReturn().getResponse().getContentAsString();
        assertThat(delta(citiesBefore, citiesAfter, "$.cities[?(@.city == 'Bengaluru')].orders")).isEqualTo(3);

        // A period is at most 366 days; lists are at most 100 long
        asAdmin(get("/api/v1/admin/orders/stats").param("fromDate", "2025-01-01").param("toDate", "2026-06-30"), null)
                .andExpect(status().isBadRequest());
        asAdmin(get("/api/v1/admin/orders/stats/restaurants").param("limit", "0"), null).andExpect(status().isBadRequest());
        asCustomer(asha, get("/api/v1/admin/orders/stats"), null).andExpect(status().isForbidden());
    }

    @Test
    void revenueOfADishCountsDeliveredOrdersPerMonthWeekAndDay() throws Exception {
        long dish = randomId();
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        java.time.LocalDate thisMonth = today.withDayOfMonth(1);
        // delivered: the 1st of this month (2 pieces, next to another dish) and the last evening of last month;
        // not counted: a cancelled order and a delivered one from two years ago (outside the 12 months)
        insertOrder("DELIVERED", thisMonth.atTime(0, 30).atZone(zone).toInstant(), dish, 2, "200.00");
        jdbcTemplate.update("INSERT INTO order_items (order_id, menu_item_id, name, food_type, unit_price, quantity, line_total)"
                + " SELECT MAX(id), ?, 'Other', 'VEG', 10.00, 1, 10.00 FROM orders", dish + 1);
        insertOrder("DELIVERED", thisMonth.minusDays(1).atTime(23, 30).atZone(zone).toInstant(), dish, 1, "100.00");
        insertOrder("CANCELLED", Instant.now(), dish, 5, "500.00");
        insertOrder("DELIVERED", thisMonth.minusYears(2).atStartOfDay(zone).toInstant(), dish, 1, "999.00");

        String monthly = asAdmin(get("/api/v1/admin/orders/stats/menu-items/{id}/revenue", dish), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.menuItemId").value(dish))
                .andExpect(jsonPath("$.period").value("MONTHLY")).andExpect(jsonPath("$.points.length()").value(12))
                .andReturn().getResponse().getContentAsString();
        assertThat(number(monthly, "$.totalRevenue")).isEqualTo(300.00);
        assertThat(number(monthly, "$.totalQuantity")).isEqualTo(3);
        assertThat(number(monthly, "$.points[11].revenue")).isEqualTo(200.00);
        assertThat(number(monthly, "$.points[11].quantity")).isEqualTo(2);
        assertThat(number(monthly, "$.points[11].orders")).isEqualTo(1);
        assertThat(number(monthly, "$.points[10].revenue")).isEqualTo(100.00);
        assertThat(number(monthly, "$.points[9].revenue")).isEqualTo(0);
        assertThat((String) JsonPath.read(monthly, "$.points[11].start")).isEqualTo(thisMonth.toString());

        asAdmin(get("/api/v1/admin/orders/stats/menu-items/{id}/revenue", dish).param("period", "DAILY"), null)
                .andExpect(jsonPath("$.points.length()").value(30))
                .andExpect(jsonPath("$.points[29].start").value(today.toString()));
        asAdmin(get("/api/v1/admin/orders/stats/menu-items/{id}/revenue", dish).param("period", "WEEKLY"), null)
                .andExpect(jsonPath("$.points.length()").value(12));
        asAdmin(get("/api/v1/admin/orders/stats/menu-items/{id}/revenue", randomId()), null)
                .andExpect(jsonPath("$.totalRevenue").value(0.0)).andExpect(jsonPath("$.points.length()").value(12));
        asCustomer(randomId(), get("/api/v1/admin/orders/stats/menu-items/{id}/revenue", dish), null)
                .andExpect(status().isForbidden());

        // Leave the shared database as it was: the other statistics tests compare totals before and after
        jdbcTemplate.update("DELETE FROM orders WHERE customer_id = ?", dish);
    }

    @Test
    void topFoodsOfACustomerCountRealRecentOrdersOfThatCustomerOnly() throws Exception {
        long customer = randomId();
        long otherCustomer = randomId();
        long biryani = randomId();
        long kulfi = randomId();
        Instant now = Instant.now();
        when(catalogLookupClient.findDetails(anyCollection())).thenReturn(Map.of(
                biryani, new MenuItemDetails(biryani, "Biryani", "http://img/b.jpg", "Mains")));

        // counted: delivered biryani x2 (price changed from 90 to 100 in the later order), placed biryani x1 and kulfi x1
        insertCustomerOrder(customer, "DELIVERED", now.minus(java.time.Duration.ofDays(10)), biryani, 2, "90.00", "180.00");
        insertCustomerOrder(customer, "PLACED", now.minus(java.time.Duration.ofDays(2)), biryani, 1, "100.00", "100.00");
        insertCustomerOrder(customer, "DELIVERED", now.minus(java.time.Duration.ofDays(1)), kulfi, 1, "50.00", "50.00");
        // not counted: cancelled, rejected, unpaid, older than 30 days
        insertCustomerOrder(customer, "CANCELLED", now.minus(java.time.Duration.ofDays(1)), kulfi, 9, "50.00", "450.00");
        insertCustomerOrder(customer, "REJECTED", now.minus(java.time.Duration.ofDays(1)), kulfi, 9, "50.00", "450.00");
        insertCustomerOrder(customer, "PAYMENT_PENDING", now, kulfi, 9, "50.00", "450.00");
        insertCustomerOrder(customer, "DELIVERED", now.minus(java.time.Duration.ofDays(45)), kulfi, 9, "50.00", "450.00");
        // another customer's order of the same dish
        insertCustomerOrder(otherCustomer, "DELIVERED", now.minus(java.time.Duration.ofDays(1)), biryani, 7, "100.00", "700.00");

        asAdmin(get("/api/v1/admin/orders/stats/customers/{id}/top-foods", customer), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(customer))
                .andExpect(jsonPath("$.period").value("MONTHLY")).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].menuItemId").value(biryani))
                .andExpect(jsonPath("$.items[0].orders").value(2)).andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].totalSpent").value(280.0))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.0))
                .andExpect(jsonPath("$.items[0].imageUrl").value("http://img/b.jpg"))
                .andExpect(jsonPath("$.items[0].categoryName").value("Mains"))
                .andExpect(jsonPath("$.items[0].lastOrderedAt").exists())
                .andExpect(jsonPath("$.items[1].menuItemId").value(kulfi))
                .andExpect(jsonPath("$.items[1].quantity").value(1)).andExpect(jsonPath("$.items[1].imageUrl").doesNotExist());
        asAdmin(get("/api/v1/admin/orders/stats/customers/{id}/top-foods", customer).param("limit", "1"), null)
                .andExpect(jsonPath("$.items.length()").value(1));
        asAdmin(get("/api/v1/admin/orders/stats/customers/{id}/top-foods", customer).param("period", "WEEKLY"), null)
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].quantity").value(1));
        asAdmin(get("/api/v1/admin/orders/stats/customers/{id}/top-foods", randomId()), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        asAdmin(get("/api/v1/admin/orders/stats/customers/{id}/top-foods", customer).param("limit", "21"), null)
                .andExpect(status().isBadRequest());
        asCustomer(customer, get("/api/v1/admin/orders/stats/customers/{id}/top-foods", customer), null)
                .andExpect(status().isForbidden());

        // Leave the shared database as it was: the other statistics tests compare totals before and after
        jdbcTemplate.update("DELETE FROM orders WHERE customer_id IN (?, ?)", customer, otherCustomer);
    }

    @Test
    void topItemsOfMyRestaurantRankRealOrdersInTheRangeAndRefuseOtherOwners() throws Exception {
        long otherRestaurant = randomId();
        long biryani = randomId();
        long kulfi = randomId();
        long raita = randomId();
        long unpaid = randomId();
        long outside = randomId();
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.LocalDate day = java.time.LocalDate.parse("2018-05-10"); // years back: no other test writes here
        when(restaurantServiceClient.findRestaurant(otherRestaurant)).thenReturn(Optional.of(
                new RestaurantSummary(otherRestaurant, 999L, "Other Kitchen", "ACTIVE", true)));

        // biryani: 2 orders, 3 portions (the later order carries the current name); kulfi: 1 order, 3 portions, so
        // it ties on quantity and ranks after biryani on orders; raita: cancelled and rejected orders count, as
        // they do in sales-summary
        insertRestaurantOrder(restaurantId, "DELIVERED", day.atTime(0, 0).atZone(zone).toInstant(),
                biryani, "Biryani old", 2, "200.00");
        insertRestaurantOrder(restaurantId, "PLACED", day.atTime(23, 59).atZone(zone).toInstant(),
                biryani, "Chicken Biryani", 1, "100.00");
        insertRestaurantOrder(restaurantId, "DELIVERED", day.atTime(12, 0).atZone(zone).toInstant(),
                kulfi, "Malai Kulfi", 3, "150.00");
        insertRestaurantOrder(restaurantId, "CANCELLED", day.atTime(9, 0).atZone(zone).toInstant(), raita, "Raita", 1, "30.00");
        insertRestaurantOrder(restaurantId, "REJECTED", day.atTime(10, 0).atZone(zone).toInstant(), raita, "Raita", 1, "30.00");
        // not counted: unpaid, and the minute before and the instant after the day
        insertRestaurantOrder(restaurantId, "PAYMENT_PENDING", day.atTime(12, 0).atZone(zone).toInstant(),
                unpaid, "Unpaid", 50, "500.00");
        insertRestaurantOrder(restaurantId, "DELIVERED", day.atStartOfDay(zone).toInstant().minusSeconds(60),
                outside, "Edge", 9, "90.00");
        insertRestaurantOrder(restaurantId, "DELIVERED", day.plusDays(1).atStartOfDay(zone).toInstant(),
                outside, "Edge", 9, "90.00");
        // another restaurant sold the same dish
        insertRestaurantOrder(otherRestaurant, "DELIVERED", day.atTime(12, 0).atZone(zone).toInstant(),
                biryani, "Chicken Biryani", 20, "2000.00");

        try {
            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId)
                    .param("from", "2018-05-10").param("to", "2018-05-10"), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                    .andExpect(jsonPath("$.from").value("2018-05-10")).andExpect(jsonPath("$.to").value("2018-05-10"))
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[0].menuItemId").value(biryani))
                    .andExpect(jsonPath("$.items[0].name").value("Chicken Biryani"))
                    .andExpect(jsonPath("$.items[0].quantity").value(3)).andExpect(jsonPath("$.items[0].orders").value(2))
                    .andExpect(jsonPath("$.items[0].revenue").value(300.0))
                    .andExpect(jsonPath("$.items[1].menuItemId").value(kulfi))
                    .andExpect(jsonPath("$.items[1].quantity").value(3)).andExpect(jsonPath("$.items[1].orders").value(1))
                    .andExpect(jsonPath("$.items[2].menuItemId").value(raita))
                    .andExpect(jsonPath("$.items[2].quantity").value(2)).andExpect(jsonPath("$.items[2].revenue").value(60.0));
            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId)
                    .param("from", "2018-05-10").param("to", "2018-05-10").param("limit", "1"), null)
                    .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].menuItemId").value(biryani));
            // the neighbouring days are in once the range reaches them
            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId)
                    .param("from", "2018-05-09").param("to", "2018-05-11").param("limit", "1"), null)
                    .andExpect(jsonPath("$.items[0].menuItemId").value(outside))
                    .andExpect(jsonPath("$.items[0].quantity").value(18));
            // today by default: nothing was ordered today
            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));

            // Another owner's restaurant and an unknown one get the answer of the other owner endpoints
            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", otherRestaurant), null).andExpect(status().isNotFound());
            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", randomId()), null).andExpect(status().isNotFound());
            asOwner(get("/api/v1/restaurants/mine/{r}/orders", otherRestaurant), null).andExpect(status().isNotFound());

            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId)
                    .param("from", "2017-05-09").param("to", "2018-05-10"), null).andExpect(status().isBadRequest()); // 367 days
            asOwner(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId).param("limit", "21"), null)
                    .andExpect(status().isBadRequest());
            asCustomer(randomId(), get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId), null)
                    .andExpect(status().isForbidden());
            asAdmin(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId), null).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/restaurants/mine/{r}/orders/top-items", restaurantId))
                    .andExpect(status().isUnauthorized());
        } finally {
            // Leave the shared database as it was: other tests compare totals before and after
            jdbcTemplate.update("DELETE FROM orders WHERE restaurant_id IN (?, ?)", restaurantId, otherRestaurant);
        }
    }

    /** Like insertOrder, but for a chosen restaurant and with the dish name on the line. */
    private void insertRestaurantOrder(long restaurant, String status, Instant createdAt, long dish, String name,
                                       int quantity, String lineTotal) {
        java.time.LocalDateTime created = java.time.LocalDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC);
        jdbcTemplate.update("INSERT INTO orders (customer_id, customer_name, restaurant_id, restaurant_name, status,"
                + " payment_method, payment_status, items_total, delivery_fee, tax_amount, total_amount, currency,"
                + " delivery_line1, delivery_city, delivery_state, delivery_postal_code, delivery_country,"
                + " idempotency_key, request_fingerprint, created_at, updated_at)"
                + " VALUES (?, 'T', ?, 'R', ?, 'CASH_ON_DELIVERY', 'PENDING', ?, 0, 0, ?, 'INR', 'x', 'c', 's', 'p', 'k',"
                + " ?, 'f', ?, ?)", randomId(), restaurant, status, lineTotal, lineTotal, UUID.randomUUID().toString(),
                created, created);
        jdbcTemplate.update("INSERT INTO order_items (order_id, menu_item_id, name, food_type, unit_price, quantity, line_total)"
                + " SELECT MAX(id), ?, ?, 'VEG', 100.00, ?, ? FROM orders", dish, name, quantity, lineTotal);
    }

    @Test
    void calendarCountsOrdersPerBusinessDayAroundLocalMidnight() throws Exception {
        long marker = randomId();
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.LocalDate day = java.time.LocalDate.parse("2019-03-10"); // years back: no other test writes here
        // 10 March: just after local midnight (still 9 March in UTC), delivered, rejected and cancelled, plus the
        // last minute of the day; 11 March 00:00 and 9 March 23:59 belong to the neighbours
        insertOrder("DELIVERED", day.atTime(0, 0).atZone(zone).toInstant(), marker, 1, "100.00");
        insertOrder("REJECTED", day.atTime(8, 0).atZone(zone).toInstant(), marker, 1, "100.00");
        insertOrder("CANCELLED", day.atTime(12, 0).atZone(zone).toInstant(), marker, 1, "100.00");
        insertOrder("PLACED", day.atTime(23, 59).atZone(zone).toInstant(), marker, 1, "100.00");
        insertOrder("PLACED", day.plusDays(1).atTime(0, 0).atZone(zone).toInstant(), marker, 1, "100.00");
        insertOrder("DELIVERED", day.minusDays(1).atTime(23, 59).atZone(zone).toInstant(), marker, 1, "100.00");
        insertOrder("DELIVERED", day.plusDays(30).atTime(10, 0).atZone(zone).toInstant(), marker, 1, "100.00");

        asAdmin(get("/api/v1/admin/orders/stats/calendar").param("from", "2019-03-10").param("to", "2019-03-10"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].date").value("2019-03-10"))
                .andExpect(jsonPath("$.days[0].placed").value(4))
                .andExpect(jsonPath("$.days[0].delivered").value(1))
                .andExpect(jsonPath("$.days[0].cancelled").value(2));
        // A wider range: only days with orders, in date order
        asAdmin(get("/api/v1/admin/orders/stats/calendar").param("from", "2019-03-09").param("to", "2019-03-11"), null)
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2019-03-09"))
                .andExpect(jsonPath("$.days[2].date").value("2019-03-11"));
        asAdmin(get("/api/v1/admin/orders/stats/calendar").param("from", "2019-03-10").param("to", "2019-03-09"), null)
                .andExpect(status().isBadRequest());
        asAdmin(get("/api/v1/admin/orders/stats/calendar").param("from", "2019-01-01").param("to", "2020-02-05"), null)
                .andExpect(status().isBadRequest()); // 401 days
        asAdmin(get("/api/v1/admin/orders/stats/calendar").param("from", "2019-03-10"), null)
                .andExpect(status().isBadRequest());
        asCustomer(marker, get("/api/v1/admin/orders/stats/calendar").param("from", "2019-03-10").param("to", "2019-03-10"),
                null).andExpect(status().isForbidden());

        jdbcTemplate.update("DELETE FROM orders WHERE customer_id = ?", marker);
    }

    private void insertCustomerOrder(long customer, String status, Instant createdAt, long dish, int quantity,
                                     String unitPrice, String lineTotal) {
        java.time.LocalDateTime created = java.time.LocalDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC);
        jdbcTemplate.update("INSERT INTO orders (customer_id, customer_name, restaurant_id, restaurant_name, status,"
                + " payment_method, payment_status, items_total, delivery_fee, tax_amount, total_amount, currency,"
                + " delivery_line1, delivery_city, delivery_state, delivery_postal_code, delivery_country,"
                + " idempotency_key, request_fingerprint, created_at, updated_at)"
                + " VALUES (?, 'T', 1, 'R', ?, 'CASH_ON_DELIVERY', 'PENDING', ?, 0, 0, ?, 'INR', 'x', 'c', 's', 'p', 'k',"
                + " ?, 'f', ?, ?)", customer, status, lineTotal, lineTotal, UUID.randomUUID().toString(), created, created);
        jdbcTemplate.update("INSERT INTO order_items (order_id, menu_item_id, name, food_type, unit_price, quantity, line_total)"
                + " SELECT MAX(id), ?, 'Dish', 'VEG', ?, ?, ? FROM orders", dish, unitPrice, quantity, lineTotal);
    }

    /** An order with one line of the dish, created at a chosen time (the API cannot back-date orders). */
    private void insertOrder(String status, Instant createdAt, long dish, int quantity, String lineTotal) {
        java.time.LocalDateTime created = java.time.LocalDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC); // stored as UTC
        jdbcTemplate.update("INSERT INTO orders (customer_id, customer_name, restaurant_id, restaurant_name, status,"
                + " payment_method, payment_status, items_total, delivery_fee, tax_amount, total_amount, currency,"
                + " delivery_line1, delivery_city, delivery_state, delivery_postal_code, delivery_country,"
                + " idempotency_key, request_fingerprint, created_at, updated_at)"
                + " VALUES (?, 'T', 1, 'R', ?, 'CASH_ON_DELIVERY', 'PENDING', ?, 0, 0, ?, 'INR', 'x', 'c', 's', 'p', 'k',"
                + " ?, 'f', ?, ?)", dish, status, lineTotal, lineTotal, UUID.randomUUID().toString(), created, created);
        jdbcTemplate.update("INSERT INTO order_items (order_id, menu_item_id, name, food_type, unit_price, quantity, line_total)"
                + " SELECT MAX(id), ?, 'Dish', 'VEG', 100.00, ?, ? FROM orders", dish, quantity, lineTotal);
    }

    private static long orderId(ResultActions placed) throws Exception {
        return ((Number) JsonPath.read(placed.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    /** How much a number grew between two responses (0 where it was missing before). */
    private static double delta(String before, String after, String path) {
        return number(after, path) - number(before, path);
    }

    private static double number(String json, String path) {
        Object value = JsonPath.read(json, path);
        if (value instanceof List<?> list) {
            value = list.isEmpty() ? 0 : list.getFirst();
        }
        return value == null ? 0 : Math.round(((Number) value).doubleValue() * 100) / 100.0;
    }

    @Test
    void aServiceThatCannotBeReachedIsLeftAloneAfterAFewFailures() {
        // A real client (not the mock) for a port nobody listens on: every call is refused
        RestClient nobodyListening = RestClient.builder().baseUrl("http://localhost:1").build();
        PromotionServiceClient client = new PromotionServiceClient(nobodyListening, remoteCalls);
        CircuitBreaker circuit = circuitBreakerRegistry.circuitBreaker("promotion-service");
        circuit.reset();
        try {
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> client.check("WELCOME100", 1L, 2L, BigDecimal.TEN))
                        .isInstanceOf(RemoteServiceException.class);
            }
            // A refused connection is tried once more (application.yml); every attempt counts for the circuit
            // breaker, so it opens after 5 refused attempts and the later calls are not even tried
            assertThat(retryRegistry.retry("promotion-service").getMetrics().getNumberOfFailedCallsWithRetryAttempt())
                    .isPositive();
            assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(5);
            assertThat(circuit.getMetrics().getNumberOfNotPermittedCalls()).isPositive();
            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            long started = System.nanoTime();
            assertThatThrownBy(() -> client.check("WELCOME100", 1L, 2L, BigDecimal.TEN))
                    .isInstanceOf(RemoteServiceException.class)
                    .hasMessageContaining("OPEN");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(50));
            assertThat(circuit.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(bulkheadRegistry.bulkhead("promotion-service").getBulkheadConfig().getMaxConcurrentCalls())
                    .isEqualTo(20);
        } finally {
            circuit.reset();
        }
    }

    @Test
    void adminFindsTheOrderCancelsItAndSeesTheAuditAndCustomerSummary() throws Exception {
        long customer = randomId();
        asCustomer(customer, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 1}").andExpect(status().isOk());
        String body = placeOrder(customer, "checkout-" + UUID.randomUUID(), "CASH_ON_DELIVERY")
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
        asOwner(put("/api/v1/restaurants/mine/{r}/orders/{o}/accept", restaurantId, orderId), null).andExpect(status().isOk());

        asAdmin(get("/api/v1/admin/orders").param("customerId", String.valueOf(customer)).param("status", "ACCEPTED"), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId));

        asAdmin(put("/api/v1/admin/orders/{o}/cancel", orderId), "{\"reason\": \"Restaurant reported a kitchen fire\"}")
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Restaurant reported a kitchen fire"));
        asAdmin(get("/api/v1/admin/orders/{o}/timeline", orderId), null)
                .andExpect(jsonPath("$[2].actorType").value("ADMIN"));
        asAdmin(get("/api/v1/admin/orders/{o}/audit-logs", orderId), null)
                .andExpect(jsonPath("$.content[0].action").value("ORDER_CANCELLED_BY_ADMIN"))
                .andExpect(jsonPath("$.content[0].oldValue").value("ACCEPTED"));
        asAdmin(get("/api/v1/admin/orders/customers/{c}/summary", customer), null)
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.cancelledOrders").value(1));
    }

    @Test
    void paymentEventMovesAPaidOrderToTheRestaurantExactlyOnce() throws Exception {
        long customer = randomId();
        long orderId = placeOnlineOrder(customer, "CARD");

        // The new order was announced (transactional outbox -> Kafka) for payment-service
        OrderCreated created = KafkaTestEvents.awaitEvent(
                consumerFactory, Topics.ORDER_CREATED, String.valueOf(orderId), OrderCreated.class);
        assertThat(created.getStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(created.getPaymentMethod()).isEqualTo("CARD");
        assertThat(created.getTotalAmount()).isEqualByComparingTo("353.95");
        assertThat(created.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getName()).isEqualTo("Chicken Biryani"));
        // What delivery-service needs to bring the food
        assertThat(created.getRestaurantName()).isEqualTo("Spice Hub");
        assertThat(created.getCustomerName()).isEqualTo("Priya Sharma");
        assertThat(created.getCustomerPhone()).isEqualTo("+919876543210");
        assertThat(created.getDeliveryAddress().getLine1()).isEqualTo("12 MG Road");
        assertThat(created.getDeliveryAddress().getCity()).isEqualTo("Bengaluru");

        // payment-service can still read the order directly (for orders it has not heard of yet)
        mockMvc.perform(get("/api/v1/internal/orders/{o}", orderId).with(TestTokens.serviceToken()))
                .andExpect(jsonPath("$.customerId").value(customer))
                .andExpect(jsonPath("$.totalAmount").value(353.95));

        // payment-service reports the result as an event
        PaymentStatusChanged paid = paymentEvent(orderId, customer, 900L, "SUCCESS");
        publish(paid);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                asOwner(get("/api/v1/restaurants/mine/{r}/orders/{o}", restaurantId, orderId), null)
                        .andExpect(jsonPath("$.status").value("PLACED")));

        // Kafka may deliver the same event again: it is recognised and changes nothing
        orderPaymentService.applyPaymentStatusChanged(paid);
        asOwner(get("/api/v1/restaurants/mine/{r}/orders/{o}", restaurantId, orderId), null)
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[1].note").value("Payment 900 received"));
    }

    @Test
    void aPaymentThatArrivesAfterTheCancellationIsRejectedSoItIsRefunded() throws Exception {
        long customer = randomId();
        long orderId = placeOnlineOrder(customer, "UPI");
        asCustomer(customer, put("/api/v1/orders/{o}/cancel", orderId), "{\"reason\": \"Took too long\"}")
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // The payment was still running and succeeds after all
        publish(paymentEvent(orderId, customer, 902L, "SUCCESS"));

        // Compensation: the order stays cancelled and payment-service is asked to give the money back
        OrderPaymentRejected rejected = KafkaTestEvents.awaitEvent(
                consumerFactory, Topics.ORDER_PAYMENT_REJECTED, String.valueOf(orderId), OrderPaymentRejected.class);
        assertThat(rejected.getPaymentId()).isEqualTo(902L);
        assertThat(rejected.getOrderStatus()).isEqualTo("CANCELLED");
        asCustomer(customer, get("/api/v1/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.timeline[2].note")
                        .value("Payment 902 arrived after the order was cancelled: refund requested"));

        // payment-service refunds and says so: the order shows it
        publish(paymentEvent(orderId, customer, 902L, "REFUNDED"));
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> asCustomer(customer, get("/api/v1/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED")));
    }

    @Test
    void anUnpaidOrderIsCancelledByTheSystemWhenThePaymentTimesOut() throws Exception {
        long customer = randomId();
        long orderId = placeOnlineOrder(customer, "WALLET");

        // expire-after is 1s in the tests; the job runs here instead of on its schedule
        await().atMost(EVENT_TIMEOUT).until(() -> {
            unpaidOrderExpiryService.cancelExpiredOrders();
            return orderRepository.findById(orderId).orElseThrow().getStatus() == OrderStatus.CANCELLED;
        });

        OrderCancelled cancelled = KafkaTestEvents.awaitEvent(
                consumerFactory, Topics.ORDER_CANCELLED, String.valueOf(orderId), OrderCancelled.class);
        assertThat(cancelled.getCancelledBy()).isEqualTo("SYSTEM");
        assertThat(cancelled.getPreviousStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(cancelled.getPaymentStatus()).isEqualTo("PENDING");
        assertThat(cancelled.getReason()).startsWith("Payment not completed within");
    }

    @Test
    void rejectingAPaidOrderPublishesOrderCancelledSoThePaymentIsRefunded() throws Exception {
        long customer = randomId();
        long orderId = placeOnlineOrder(customer, "UPI");
        publish(paymentEvent(orderId, customer, 901L, "SUCCESS"));
        await().atMost(EVENT_TIMEOUT).untilAsserted(() ->
                asOwner(get("/api/v1/restaurants/mine/{r}/orders/{o}", restaurantId, orderId), null)
                        .andExpect(jsonPath("$.status").value("PLACED")));

        mockMvc.perform(put("/api/v1/restaurants/mine/{r}/orders/{o}/reject", restaurantId, orderId)
                        .with(TestTokens.as(OWNER, "RESTAURANT_ADMIN"))
                        .header("X-Request-Id", "it-reject-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Kitchen closed early\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        OrderCancelled event = KafkaTestEvents.awaitEvent(
                consumerFactory, Topics.ORDER_CANCELLED, String.valueOf(orderId), OrderCancelled.class);
        assertThat(event.getFinalStatus()).isEqualTo("REJECTED");
        assertThat(event.getPreviousStatus()).isEqualTo("PLACED");
        assertThat(event.getCancelledBy()).isEqualTo("RESTAURANT");
        assertThat(event.getReason()).isEqualTo("Kitchen closed early");
        assertThat(event.getPaymentMethod()).isEqualTo("UPI");
        assertThat(event.getPaymentStatus()).isEqualTo("PAID");
        assertThat(event.getTotalAmount()).isEqualByComparingTo("353.95");
        assertThat(event.getCustomerId()).isEqualTo(customer);
        assertThat(event.getRequestId()).isEqualTo("it-reject-001");
    }

    @Test
    void deliveryPartnerEventsFinishACashOrderAndMarkItPaid() throws Exception {
        long customer = randomId();
        long partner = randomId();
        asCustomer(customer, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 1}").andExpect(status().isOk());
        String body = placeOrder(customer, "checkout-" + UUID.randomUUID(), "CASH_ON_DELIVERY")
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
        for (String step : List.of("accept", "preparing", "ready")) {
            asOwner(put("/api/v1/restaurants/mine/{r}/orders/{o}/" + step, restaurantId, orderId), null)
                    .andExpect(status().isOk());
        }

        // delivery-service reports the delivery as it goes
        publish(deliveryEvent(orderId, customer, partner, "ASSIGNED"));
        publish(deliveryEvent(orderId, customer, partner, "PICKED_UP"));
        DeliveryStatusChanged delivered = deliveryEvent(orderId, customer, partner, "DELIVERED");
        publish(delivered);
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> asCustomer(customer, get("/api/v1/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID")));

        // Kafka may deliver the same event again: it is recognised and changes nothing
        publish(delivered);
        List<OrderStatusChanged> statusEvents = KafkaTestEvents.awaitEvents(consumerFactory,
                Topics.ORDER_STATUS_CHANGED, String.valueOf(orderId), OrderStatusChanged.class, 6);
        assertThat(statusEvents).extracting(OrderStatusChanged::getStatus).endsWith("OUT_FOR_DELIVERY", "DELIVERED");
        assertThat(statusEvents.getLast().getChangedBy()).isEqualTo("DELIVERY_PARTNER");
        asCustomer(customer, get("/api/v1/orders/{o}", orderId), null)
                .andExpect(jsonPath("$.timeline[*].note").value(org.hamcrest.Matchers.hasItems(
                        "Delivery assigned to Ravi Kumar", "Picked up by Ravi Kumar", "Delivered by Ravi Kumar",
                        "Cash collected: INR 353.95")))
                .andExpect(jsonPath("$.timeline.length()").value(8))
                .andExpect(jsonPath("$.timeline[7].actorUserId").value(partner));
    }

    @Test
    void cartRefusesItemsFromASecondRestaurant() throws Exception {
        long customer = randomId();
        long otherRestaurant = randomId();
        catalog.put(999L, new MenuItemSummary(999L, otherRestaurant, "Pizza", new BigDecimal("199.00"), "VEG", "ACTIVE", true));

        asCustomer(customer, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 1}").andExpect(status().isOk());
        asCustomer(customer, put("/api/v1/cart/items/{id}", 999L), "{\"quantity\": 1}").andExpect(status().isConflict());
        asCustomer(customer, delete("/api/v1/cart"), null).andExpect(status().isNoContent());
        asCustomer(customer, put("/api/v1/cart/items/{id}", 999L), "{\"quantity\": 1}").andExpect(status().isOk());
    }

    /** Two identical checkouts at the same moment (double click, retry): exactly one order is created. */
    @Test
    void simultaneousCheckoutsWithTheSameKeyCreateOneOrder() throws Exception {
        long customer = randomId();
        cartService.setItemQuantity(customer, BIRYANI, 1);
        String key = "checkout-" + UUID.randomUUID();
        PlaceOrderRequest request = new PlaceOrderRequest(55L, PaymentMethod.CASH_ON_DELIVERY, null);

        CountDownLatch startTogether = new CountDownLatch(1);
        Callable<Long> checkout = () -> {
            startTogether.await();
            return orderPlacementService.placeOrder(customer, key, request).order().id();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = pool.submit(checkout);
            Future<Long> second = pool.submit(checkout);
            startTogether.countDown();

            // Both callers get the same order back: one created it, the other received it as a replay.
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThat(orderRepository.findAll().stream().filter(order -> order.getCustomerId() == customer)).hasSize(1);
    }

    /** An online-payment order for one biryani (353.95), waiting for payment. */
    private long placeOnlineOrder(long customer, String paymentMethod) throws Exception {
        asCustomer(customer, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 1}").andExpect(status().isOk());
        String body = placeOrder(customer, "checkout-" + UUID.randomUUID(), paymentMethod)
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** What payment-service publishes when a payment reaches a final status. */
    private static PaymentStatusChanged paymentEvent(long orderId, long customer, long paymentId, String status) {
        return PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setPaymentId(paymentId)
                .setOrderId(orderId)
                .setCustomerId(customer)
                .setStatus(status)
                .setMethod("UPI")
                .setAmount(new BigDecimal("353.95"))
                .setRefundedAmount(new BigDecimal("REFUNDED".equals(status) ? "353.95" : "0.00"))
                .setCurrency("INR")
                .build();
    }

    private void publish(PaymentStatusChanged event) throws Exception {
        kafkaTemplate.send(Topics.PAYMENT_STATUS_CHANGED, String.valueOf(event.getOrderId()), event).get();
    }

    /** What delivery-service publishes as the partner works through the delivery. */
    private static DeliveryStatusChanged deliveryEvent(long orderId, long customer, long partner, String status) {
        return DeliveryStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setDeliveryId(orderId + 1)
                .setOrderId(orderId)
                .setCustomerId(customer)
                .setRestaurantId(1L)
                .setStatus(status)
                .setPartnerUserId(partner)
                .setPartnerName("Ravi Kumar")
                .setVehicleType("BIKE")
                .build();
    }

    private void publish(DeliveryStatusChanged event) throws Exception {
        kafkaTemplate.send(Topics.DELIVERY_STATUS_CHANGED, String.valueOf(event.getOrderId()), event).get();
    }

    @Test
    void onlyAValidAccessTokenOfThePlatformNamesTheCaller() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", TestJwts.bearer(4242, "CUSTOMER")))
                .andExpect(status().isOk());

        List<String> refused = List.of(
                TestJwts.tokenSignedByAnotherKey(4242, "CUSTOMER"),
                TestJwts.token(4242, "CUSTOMER", claims -> claims.issuedAt(Instant.now().minusSeconds(1_200))
                        .expiresAt(Instant.now().minusSeconds(300))),
                TestJwts.token(4242, "CUSTOMER", claims -> claims.issuer("https://somebody.else")),
                TestJwts.token(4242, "CUSTOMER", claims -> claims.audience(List.of("another-api"))));
        for (String token : refused) {
            mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        // A valid token of another role is not enough for the customers' API
        mockMvc.perform(get("/api/v1/orders").header("Authorization", TestJwts.bearer(3, "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theInternalApisAreForTheOtherServicesOnly() throws Exception {
        mockMvc.perform(get("/api/v1/internal/orders/{o}", 999_999L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/internal/orders/{o}", 999_999L).header("X-Service-Token", "a-guess"))
                .andExpect(status().isUnauthorized());
        // Even an administrator's token is not the service token: signed in, but not allowed
        mockMvc.perform(get("/api/v1/internal/orders/{o}", 999_999L).with(TestTokens.as(ADMIN, "PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/internal/orders/{o}", 999_999L).with(TestTokens.serviceToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCircuitBreakersStateIsForAdministratorsWhileHealthStaysOpen() throws Exception {
        // Open to monitoring (UP or DOWN, never a question of signing in)
        int health = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(health).isNotIn(401, 403);

        mockMvc.perform(get("/actuator/circuitbreakers"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/circuitbreakers").with(TestTokens.as(OWNER, "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/circuitbreakers").with(TestTokens.as(ADMIN, "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void aCustomersDashboardAddsUpTheirOwnOrdersOnly() throws Exception {
        long customer = randomId();
        long someoneElse = randomId();
        asCustomer(customer, put("/api/v1/cart/items/{id}", BIRYANI), "{\"quantity\": 2}").andExpect(status().isOk());
        asCustomer(customer, put("/api/v1/cart/items/{id}", KULFI), "{\"quantity\": 1}").andExpect(status().isOk());
        placeOrder(customer, "dashboard-" + UUID.randomUUID(), "CASH_ON_DELIVERY").andExpect(status().isCreated());
        when(catalogServiceClient.findMenuItemCards(anyCollection())).thenReturn(Map.of(
                BIRYANI, new MenuItemCard(BIRYANI, restaurantId, "Chicken Biryani", new BigDecimal("319.00"), "NON_VEG",
                        "ACTIVE", true, "http://img/b.jpg", "Biryani"),
                KULFI, new MenuItemCard(KULFI, restaurantId, "Malai Kulfi", new BigDecimal("89.00"), "VEG", "ACTIVE",
                        false, null, "Desserts")));

        // All time: one order on its way, nothing delivered yet (so nothing spent)
        asCustomer(customer, get("/api/v1/orders/summary"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.activeOrders").value(1))
                .andExpect(jsonPath("$.deliveredOrders").value(0))
                .andExpect(jsonPath("$.totalSpent").value(0.0))
                .andExpect(jsonPath("$.lastOrderAt").exists());
        // Today (the default period), day by day, grouped in the database
        asCustomer(customer, get("/api/v1/orders/activity"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.ordersByStatus.PLACED").value(1))
                .andExpect(jsonPath("$.spent").value(0.0))
                .andExpect(jsonPath("$.perDay.length()").value(1))
                .andExpect(jsonPath("$.perDay[0].orders").value(1));
        // Most portions first (two biryanis), with today's price and stock
        asCustomer(customer, get("/api/v1/orders/favourite-dishes"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].menuItemId").value(BIRYANI))
                .andExpect(jsonPath("$[0].price").value(319.00))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].restaurantName").value("Spice Hub"))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[1].menuItemId").value(KULFI))
                .andExpect(jsonPath("$[1].available").value(false));

        // Someone else's dashboard knows nothing of it
        asCustomer(someoneElse, get("/api/v1/orders/summary"), null).andExpect(jsonPath("$.totalOrders").value(0));
        asCustomer(someoneElse, get("/api/v1/orders/activity"), null).andExpect(jsonPath("$.totalOrders").value(0));
        asCustomer(someoneElse, get("/api/v1/orders/favourite-dishes"), null)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void frequentPicksCountHowManyOrdersHadEachDish() throws Exception {
        long customer = randomId();
        long dosa = randomId();
        long lassi = randomId();
        long kulfi = randomId();
        long raita = randomId();
        Instant now = Instant.now();
        when(catalogServiceClient.findMenuItemCards(anyCollection())).thenReturn(Map.of());

        // dosa and lassi: in two orders each, one portion each time; dosa was ordered last, so it comes first
        insertCustomerOrder(customer, "DELIVERED", now.minus(java.time.Duration.ofDays(9)), lassi, 1, "60.00", "60.00");
        insertCustomerOrder(customer, "DELIVERED", now.minus(java.time.Duration.ofDays(8)), lassi, 1, "60.00", "60.00");
        insertCustomerOrder(customer, "DELIVERED", now.minus(java.time.Duration.ofDays(3)), dosa, 1, "120.00", "120.00");
        insertCustomerOrder(customer, "PLACED", now.minus(java.time.Duration.ofDays(1)), dosa, 1, "120.00", "120.00");
        // kulfi: six portions, but in one order (on two lines of it): one order, after the dishes of two orders
        insertCustomerOrder(customer, "DELIVERED", now.minus(java.time.Duration.ofDays(2)), kulfi, 3, "50.00", "150.00");
        jdbcTemplate.update("INSERT INTO order_items (order_id, menu_item_id, name, food_type, unit_price, quantity, line_total)"
                + " SELECT MAX(id), ?, 'Dish', 'VEG', 50.00, 3, 150.00 FROM orders", kulfi);
        // raita: only in orders that do not count (cancelled, declined, never paid)
        insertCustomerOrder(customer, "CANCELLED", now.minus(java.time.Duration.ofDays(5)), raita, 2, "40.00", "80.00");
        insertCustomerOrder(customer, "REJECTED", now.minus(java.time.Duration.ofDays(4)), raita, 2, "40.00", "80.00");
        insertCustomerOrder(customer, "PAYMENT_PENDING", now, raita, 2, "40.00", "80.00");

        asCustomer(customer, get("/api/v1/orders/favourite-dishes"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].menuItemId").value(dosa))
                .andExpect(jsonPath("$[0].timesOrdered").value(2))
                .andExpect(jsonPath("$[1].menuItemId").value(lassi))
                .andExpect(jsonPath("$[1].timesOrdered").value(2))
                .andExpect(jsonPath("$[2].menuItemId").value(kulfi))
                .andExpect(jsonPath("$[2].timesOrdered").value(1))
                .andExpect(jsonPath("$[2].quantity").value(6))
                // Not on any menu now (the catalog does not know them): no price, cannot be added
                .andExpect(jsonPath("$[0].price").doesNotExist())
                .andExpect(jsonPath("$[0].available").value(false));

        // Leave the shared database as it was: the other statistics tests compare totals before and after
        jdbcTemplate.update("DELETE FROM orders WHERE customer_id = ?", customer);
    }

    private ResultActions placeOrder(long customer, String key, String paymentMethod) throws Exception {
        return asCustomer(customer, post("/api/v1/orders").header("Idempotency-Key", key),
                "{\"addressId\": 55, \"paymentMethod\": \"" + paymentMethod + "\"}");
    }

    private ResultActions asCustomer(long customer, MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.with(TestTokens.as(customer, "CUSTOMER")), json);
    }

    private ResultActions asOwner(MockHttpServletRequestBuilder request, String json) throws Exception {
        return perform(request.with(TestTokens.as(OWNER, "RESTAURANT_ADMIN")), json);
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
