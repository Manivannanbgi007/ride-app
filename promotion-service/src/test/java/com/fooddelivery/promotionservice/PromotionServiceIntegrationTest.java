package com.fooddelivery.promotionservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.promotionservice.entity.RedemptionStatus;
import com.fooddelivery.promotionservice.repository.CouponRedemptionRepository;
import com.fooddelivery.promotionservice.repository.CouponRepository;
import com.fooddelivery.promotionservice.repository.ProcessedEventRepository;
import com.fooddelivery.promotionservice.service.ReservationCleanupService;
import com.jayway.jsonpath.JsonPath;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real MySQL and Kafka (Testcontainers). The tests play order-service: they check and
 * reserve coupons over the internal API, and publish the order events that follow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PromotionServiceIntegrationTest {

    private static final long ADMIN = 1L;
    private static final long RESTAURANT = 10L;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponRedemptionRepository redemptionRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private ReservationCleanupService reservationCleanupService;

    @Test
    void aCouponIsCheckedReservedAndItsLimitsHold() throws Exception {
        String code = createCoupon("FIXED", "100.00", null, "499.00", 2, 1);
        mockMvc.perform(get("/api/v1/coupons"))
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].summary")
                        .value(org.hamcrest.Matchers.contains("100.00 off")));

        long first = randomId();
        // Too small an order: the customer is told how much is missing
        check(code, first, "379.00")
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.reason").value("Add items worth 120.00 more to use this coupon (minimum order 499.00)"));
        check(code.toLowerCase(), first, "599.00")
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.discountAmount").value(100.00));

        // Reserved once per checkout: a retried checkout gets the same reservation, not a second use
        String key = "checkout:" + first + ":" + UUID.randomUUID();
        long reservationId = reservationId(reserve(key, code, first, "599.00").andExpect(status().isOk()));
        reserve(key, code, first, "599.00").andExpect(jsonPath("$.reservationId").value(reservationId));
        assertThat(timesUsed(code)).isEqualTo(1);

        // One per customer; two in total
        reserve("checkout:" + first + ":" + UUID.randomUUID(), code, first, "599.00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You have already used this coupon"));
        reserve("checkout:2:" + UUID.randomUUID(), code, randomId(), "650.00").andExpect(status().isOk());
        reserve("checkout:3:" + UUID.randomUUID(), code, randomId(), "650.00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("This coupon has been fully used"));
        assertThat(timesUsed(code)).isEqualTo(2);
    }

    @Test
    void theOrderDecidesWhetherTheUseCounts() throws Exception {
        String code = createCoupon("PERCENTAGE", "20", "150.00", "0", null, 1);
        long customer = randomId();
        long orderId = randomId();
        long reservationId = reservationId(reserve("checkout:" + customer + ":a", code, customer, "500.00"));

        // Placed, then cancelled: the use is given back and the customer may use the coupon again
        publish(Topics.ORDER_CREATED, orderId, orderCreated(orderId, customer, reservationId));
        awaitStatus(reservationId, RedemptionStatus.APPLIED);
        publish(Topics.ORDER_CANCELLED, orderId, orderCancelled(orderId, customer, reservationId));
        awaitStatus(reservationId, RedemptionStatus.RELEASED);
        assertThat(timesUsed(code)).isZero();

        // Placed and delivered: the use is final
        long secondOrder = randomId();
        long second = reservationId(reserve("checkout:" + customer + ":b", code, customer, "1000.00"));
        publish(Topics.ORDER_CREATED, secondOrder, orderCreated(secondOrder, customer, second));
        awaitStatus(second, RedemptionStatus.APPLIED);
        publish(Topics.ORDER_STATUS_CHANGED, secondOrder, delivered(secondOrder, customer));
        awaitStatus(second, RedemptionStatus.REDEEMED);

        long couponId = couponRepository.findByCode(code).orElseThrow().getId();
        asAdmin(get("/api/v1/admin/coupons/{id}", couponId), null)
                .andExpect(jsonPath("$.coupon.timesUsed").value(1))
                .andExpect(jsonPath("$.usesByStatus.REDEEMED").value(1))
                .andExpect(jsonPath("$.usesByStatus.RELEASED").value(1))
                // 20% of 1000, capped at 150
                .andExpect(jsonPath("$.discountGiven").value(150.00))
                .andExpect(jsonPath("$.customers").value(1));
        asAdmin(get("/api/v1/admin/coupons/{id}/redemptions", couponId).param("status", "RELEASED"), null)
                .andExpect(jsonPath("$.content[0].orderId").value(orderId))
                .andExpect(jsonPath("$.content[0].releaseReason").value("Order cancelled: Changed my mind"));
    }

    @Test
    void aCancellationReadBeforeTheOrderStillGivesTheUseBack() throws Exception {
        String code = createCoupon("FIXED", "50.00", null, "0", null, null);
        long customer = randomId();
        long orderId = randomId();
        long reservationId = reservationId(reserve("checkout:" + customer + ":x", code, customer, "300.00"));

        // Two topics, any order: OrderCancelled first, OrderCreated after it
        publish(Topics.ORDER_CANCELLED, orderId, orderCancelled(orderId, customer, reservationId));
        awaitStatus(reservationId, RedemptionStatus.RELEASED);
        OrderCreated created = orderCreated(orderId, customer, reservationId);
        publish(Topics.ORDER_CREATED, orderId, created);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(created.getEventId()));

        assertThat(redemptionRepository.findById(reservationId).orElseThrow().getStatus()).isEqualTo(RedemptionStatus.RELEASED);
        assertThat(timesUsed(code)).isZero();
    }

    @Test
    void aReservationThatNeverGotAnOrderIsGivenBack() throws Exception {
        String code = createCoupon("FIXED", "50.00", null, "0", 1, null);
        long customer = randomId();
        String key = "checkout:" + customer + ":abandoned";
        long reservationId = reservationId(reserve(key, code, customer, "300.00"));

        // order-service reports the failed checkout
        mockMvc.perform(delete("/api/v1/internal/coupon-reservations/{key}", key)
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isNoContent());
        assertThat(timesUsed(code)).isZero();
        assertThat(redemptionRepository.findById(reservationId).orElseThrow().getStatus()).isEqualTo(RedemptionStatus.EXPIRED);

        // The customer retries the same checkout: it takes the use again
        reserve(key, code, customer, "300.00").andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.status").value("RESERVED"));
        assertThat(timesUsed(code)).isEqualTo(1);
        mockMvc.perform(delete("/api/v1/internal/coupon-reservations/{key}", key)
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isNoContent());
        assertThat(timesUsed(code)).isZero();

        // Or it could not: the clean-up gives the use back (expire-after is 2s in the tests)
        long other = reservationId(reserve("checkout:" + randomId() + ":lost", code, randomId(), "300.00"));
        assertThat(timesUsed(code)).isEqualTo(1);
        await().atMost(TIMEOUT).until(() -> {
            reservationCleanupService.expireAbandonedReservations();
            return redemptionRepository.findById(other).orElseThrow().getStatus() == RedemptionStatus.EXPIRED;
        });
        assertThat(timesUsed(code)).isZero();

        // The order did exist after all (its event was late): the customer got the discount, the use counts again
        long orderId = randomId();
        publish(Topics.ORDER_CREATED, orderId, orderCreated(orderId, customer, other));
        awaitStatus(other, RedemptionStatus.APPLIED);
        assertThat(timesUsed(code)).isEqualTo(1);
    }

    @Test
    void manyCustomersCheckingOutAtOnceCannotOverspendTheLastUse() throws Exception {
        String code = createCoupon("FIXED", "100.00", null, "0", 1, null);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                long customer = randomId();
                Callable<Integer> checkout = () -> {
                    startTogether.await();
                    return reserve("checkout:" + customer + ":race", code, customer, "500.00")
                            .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(checkout));
            }
            startTogether.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
            assertThat(statuses).filteredOn(status -> status == 422).hasSize(5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(timesUsed(code)).isEqualTo(1);
    }

    @Test
    void adminsManageCouponsAndEveryChangeIsAudited() throws Exception {
        String code = createCoupon("FIXED", "100.00", null, "499.00", 100, 1);
        long couponId = couponRepository.findByCode(code).orElseThrow().getId();

        asAdmin(put("/api/v1/admin/coupons/{id}/deactivate", couponId), "{\"reason\": \"Campaign paused\"}")
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.availability").value("INACTIVE"));
        check(code, randomId(), "600.00").andExpect(jsonPath("$.reason").value("This coupon is not active"));
        asAdmin(put("/api/v1/admin/coupons/{id}/activate", couponId), "{\"reason\": \"Campaign resumed\"}")
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        asAdmin(put("/api/v1/admin/coupons/{id}", couponId), """
                {"discountType": "PERCENTAGE", "discountValue": 25, "maxDiscount": 120, "minOrderAmount": 299,
                 "validFrom": "%s", "validUntil": "%s", "usageLimit": 100, "perCustomerLimit": 2, "version": 2}
                """.formatted(today.minusDays(1), today.plusDays(10)))
                .andExpect(jsonPath("$.summary").value("25% off, up to 120.00"));
        asAdmin(put("/api/v1/admin/coupons/{id}", couponId), """
                {"discountType": "FIXED", "discountValue": 10, "validFrom": "%s", "validUntil": "%s", "version": 0}
                """.formatted(today, today)).andExpect(status().isConflict());
        asAdmin(post("/api/v1/admin/coupons"), couponJson(code, "FIXED", "1", null, "0", null, null))
                .andExpect(status().isConflict());

        asAdmin(get("/api/v1/admin/coupons/{id}/audit-logs", couponId), null)
                .andExpect(jsonPath("$.content[*].action").value(org.hamcrest.Matchers.contains(
                        "COUPON_UPDATED", "COUPON_ACTIVATED", "COUPON_DEACTIVATED", "COUPON_CREATED")));
        asAdmin(get("/api/v1/admin/coupons").param("code", code.substring(0, 6).toLowerCase()), null)
                .andExpect(jsonPath("$.content[0].code").value(code));
    }

    @Test
    void couponsWhoseDatesOverlapAPeriodAreFound() throws Exception {
        String marker = "CAL" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        // Years back, so the other tests' coupons never match. The period is 10-20 March 2018.
        createCouponValid(marker + "BEFORE", "2018-03-01", "2018-03-09");   // ends the day before: outside
        createCouponValid(marker + "EDGEIN", "2018-03-01", "2018-03-10");   // ends on the first day: inside
        createCouponValid(marker + "STARTS", "2018-03-05", "2018-03-12");   // starts before, ends inside
        createCouponValid(marker + "INSIDE", "2018-03-12", "2018-03-14");
        createCouponValid(marker + "ENDS", "2018-03-18", "2018-04-30");     // starts inside, ends after
        createCouponValid(marker + "COVERS", "2018-01-01", "2018-12-31");   // starts before and ends after
        createCouponValid(marker + "EDGEOUT", "2018-03-20", "2018-03-25");  // starts on the last day: inside
        createCouponValid(marker + "AFTER", "2018-03-21", "2018-03-25");    // starts the day after: outside

        asAdmin(get("/api/v1/admin/coupons").param("code", marker).param("activeFrom", "2018-03-10")
                .param("activeTo", "2018-03-20").param("size", "100").param("sort", "validFrom,asc"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code").value(org.hamcrest.Matchers.containsInAnyOrder(
                        marker + "EDGEIN", marker + "STARTS", marker + "INSIDE", marker + "ENDS", marker + "COVERS",
                        marker + "EDGEOUT")));
        // Only one end of the period: open-ended
        asAdmin(get("/api/v1/admin/coupons").param("code", marker).param("activeFrom", "2018-03-21"), null)
                .andExpect(jsonPath("$.content.length()").value(4)); // ENDS, COVERS, EDGEOUT, AFTER
        asAdmin(get("/api/v1/admin/coupons").param("code", marker).param("activeTo", "2018-03-01"), null)
                .andExpect(jsonPath("$.content.length()").value(3)); // BEFORE, EDGEIN, COVERS
        asAdmin(get("/api/v1/admin/coupons").param("code", marker).param("activeFrom", "2018-03-20")
                .param("activeTo", "2018-03-10"), null).andExpect(status().isBadRequest());
        asAdmin(get("/api/v1/admin/coupons").param("activeFrom", "soon"), null).andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private void createCouponValid(String code, String validFrom, String validUntil) throws Exception {
        asAdmin(post("/api/v1/admin/coupons"), """
                {"code": "%s", "discountType": "FIXED", "discountValue": 10, "validFrom": "%s", "validUntil": "%s"}
                """.formatted(code, validFrom, validUntil)).andExpect(status().isCreated());
    }

    private String createCoupon(String type, String value, String maxDiscount, String minOrder, Integer usageLimit,
                                Integer perCustomer) throws Exception {
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        asAdmin(post("/api/v1/admin/coupons"), couponJson(code, type, value, maxDiscount, minOrder, usageLimit, perCustomer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));
        return code;
    }

    private static String couponJson(String code, String type, String value, String maxDiscount, String minOrder,
                                     Integer usageLimit, Integer perCustomer) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        return """
                {"code": "%s", "description": "Test coupon", "discountType": "%s", "discountValue": %s,
                 "maxDiscount": %s, "minOrderAmount": %s, "validFrom": "%s", "validUntil": "%s",
                 "usageLimit": %s, "perCustomerLimit": %s}
                """.formatted(code, type, value, maxDiscount, minOrder, today.minusDays(1), today.plusDays(30),
                usageLimit, perCustomer);
    }

    private ResultActions check(String code, long customer, String itemsTotal) throws Exception {
        return perform(post("/api/v1/internal/coupons/check").with(TestTokens.serviceToken()), """
                {"code": "%s", "customerId": %d, "restaurantId": %d, "itemsTotal": %s}
                """.formatted(code, customer, RESTAURANT, itemsTotal));
    }

    private ResultActions reserve(String key, String code, long customer, String itemsTotal) throws Exception {
        return perform(post("/api/v1/internal/coupon-reservations").with(TestTokens.serviceToken()), """
                {"reservationKey": "%s", "code": "%s", "customerId": %d, "restaurantId": %d, "itemsTotal": %s}
                """.formatted(key, code, customer, RESTAURANT, itemsTotal));
    }

    private static long reservationId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.reservationId")).longValue();
    }

    private int timesUsed(String code) {
        return couponRepository.findByCode(code).orElseThrow().getTimesUsed();
    }

    private void awaitStatus(long reservationId, RedemptionStatus status) {
        await().atMost(TIMEOUT).until(() ->
                redemptionRepository.findById(reservationId).orElseThrow().getStatus() == status);
    }

    private static OrderCreated orderCreated(long orderId, long customer, long reservationId) {
        return OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customer).setRestaurantId(RESTAURANT)
                .setStatus("PLACED").setPaymentMethod("CASH_ON_DELIVERY").setItems(List.of())
                .setItemsTotal(new BigDecimal("500.00")).setDeliveryFee(new BigDecimal("0.00"))
                .setTaxAmount(new BigDecimal("20.00")).setTotalAmount(new BigDecimal("420.00")).setCurrency("INR")
                .setCouponCode("TEST").setDiscountAmount(new BigDecimal("100.00")).setCouponReservationId(reservationId)
                .build();
    }

    private static OrderCancelled orderCancelled(long orderId, long customer, long reservationId) {
        return OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customer).setRestaurantId(RESTAURANT)
                .setFinalStatus("CANCELLED").setPreviousStatus("PLACED").setCancelledBy("CUSTOMER")
                .setReason("Changed my mind").setPaymentMethod("CASH_ON_DELIVERY").setPaymentStatus("PENDING")
                .setTotalAmount(new BigDecimal("420.00")).setCurrency("INR").setCouponReservationId(reservationId)
                .build();
    }

    private static OrderStatusChanged delivered(long orderId, long customer) {
        return OrderStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerId(customer).setRestaurantId(RESTAURANT).setRestaurantName("Spice Hub")
                .setStatus("DELIVERED").setPreviousStatus("OUT_FOR_DELIVERY").setChangedBy("DELIVERY_PARTNER").build();
    }

    private void publish(String topic, long key, SpecificRecord event) throws Exception {
        kafkaTemplate.send(topic, String.valueOf(key), event).get();
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
