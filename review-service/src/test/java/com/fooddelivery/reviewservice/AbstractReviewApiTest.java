package com.fooddelivery.reviewservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.fooddelivery.reviewservice.client.MenuItemLookup;
import com.fooddelivery.reviewservice.client.OrderLookup;
import com.fooddelivery.reviewservice.client.OrderReviewInfo;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real MySQL (Testcontainers), for the tests of editing reviews and of the public ratings.
 * order-service and catalog-service are replaced by mocks, and the clock is a mock too.
 * <p>
 * All tests share one database, so every test makes its own restaurants, customers and dishes from one counter that
 * starts far above the ids of ReviewServiceIntegrationTest: filters by restaurant (or dish) keep the data apart.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
abstract class AbstractReviewApiTest {

    private static final AtomicLong IDS = new AtomicLong(50_000_000);

    @Autowired
    protected MockMvc mockMvc;
    @MockitoBean
    protected OrderLookup orderLookup;
    @MockitoBean
    protected MenuItemLookup menuItemLookup;
    @MockitoBean
    protected Clock clock;

    /** A saved review and the order it is about. */
    protected record Posted(long reviewId, long orderId) {
    }

    @BeforeEach
    void setUpClockAndCatalog() {
        at("2026-10-05T10:00:00Z");
        when(menuItemLookup.findMenuItems(any())).thenReturn(Map.of());
    }

    protected static long nextId() {
        return IDS.incrementAndGet();
    }

    protected static String dish(long menuItemId, int rating) {
        return "{\"menuItemId\": %d, \"rating\": %d}".formatted(menuItemId, rating);
    }

    /** The mocked clock says it is this moment. */
    protected void at(String instant) {
        when(clock.instant()).thenReturn(Instant.parse(instant));
    }

    /** Order-service knows this order; its dishes are called "Dish id". Returns the order id. */
    protected long stubOrder(long customerId, String customerName, long restaurantId, String status, long... menuItemIds) {
        long orderId = nextId();
        OrderReviewInfo.Line[] lines = Arrays.stream(menuItemIds)
                .mapToObj(id -> new OrderReviewInfo.Line(id, "Dish " + id))
                .toArray(OrderReviewInfo.Line[]::new);
        when(orderLookup.findOrder(orderId)).thenReturn(Optional.of(new OrderReviewInfo(orderId, customerId,
                customerName, restaurantId, "Restaurant " + restaurantId, status, Arrays.asList(lines))));
        return orderId;
    }

    /** A new customer ("Customer id") reviews a delivered order of the restaurant at the given time. */
    protected Posted review(long restaurantId, int rating, String instant, String dishesJson, long... menuItemIds)
            throws Exception {
        long customer = nextId();
        return reviewBy(customer, "Customer " + customer, restaurantId, rating, instant, dishesJson, menuItemIds);
    }

    protected Posted reviewBy(long customer, String customerName, long restaurantId, int rating, String instant,
                              String dishesJson, long... menuItemIds) throws Exception {
        at(instant);
        long orderId = stubOrder(customer, customerName, restaurantId, "DELIVERED", menuItemIds);
        String response = mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": %d, "comment": "Review of %d", "tags": [], "dishRatings": [%s]}
                        """.formatted(orderId, rating, orderId, dishesJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Posted(((Number) JsonPath.read(response, "$.id")).longValue(), orderId);
    }

    protected MockHttpServletRequestBuilder customerPost(long customerId, String body) {
        return post("/api/v1/reviews")
                .with(TestTokens.as(customerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder customerPut(long customerId, long reviewId, String body) {
        return put("/api/v1/reviews/" + reviewId)
                .with(TestTokens.as(customerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder customerDelete(long customerId, long reviewId) {
        return delete("/api/v1/reviews/" + reviewId)
                .with(TestTokens.as(customerId, "CUSTOMER"));
    }

    protected MockHttpServletRequestBuilder customerGet(long customerId, String path) {
        return get(path).with(TestTokens.as(customerId, "CUSTOMER"));
    }

    /** What a visitor who is not signed in sends: no identity headers at all. */
    protected MockHttpServletRequestBuilder publicGet(String path) {
        return get(path);
    }

    /** A call of another service into an internal API: with the services' shared token. */
    protected MockHttpServletRequestBuilder internalGet(String path) {
        return get(path).with(TestTokens.serviceToken());
    }
}
