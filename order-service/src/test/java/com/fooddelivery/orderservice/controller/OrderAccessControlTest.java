package com.fooddelivery.orderservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.fooddelivery.orderservice.TestTokens;
import com.fooddelivery.orderservice.config.SecurityConfig;
import com.fooddelivery.orderservice.service.AdminOrderService;
import com.fooddelivery.orderservice.service.CartService;
import com.fooddelivery.orderservice.service.CustomerInsightsService;
import com.fooddelivery.orderservice.service.CustomerOrderService;
import com.fooddelivery.orderservice.service.CustomerTopFoodsService;
import com.fooddelivery.orderservice.service.MenuItemRevenueService;
import com.fooddelivery.orderservice.service.OrderPlacementService;
import com.fooddelivery.orderservice.service.OrderStatsService;
import com.fooddelivery.orderservice.service.RestaurantOrderService;
import com.fooddelivery.orderservice.service.RevenuePeriod;
import com.fooddelivery.orderservice.service.TopFoodsPeriod;
import com.fooddelivery.orderservice.web.SecurityProblemHandlers;
import com.fooddelivery.orderservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: which role may call which API area, Idempotency-Key handling and validation. */
@WebMvcTest({CartController.class, CustomerOrderController.class, RestaurantOrderController.class,
        AdminOrderController.class, AdminOrderStatsController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class OrderAccessControlTest {

    private static final String VALID_ORDER = """
            {"addressId": 55, "paymentMethod": "CASH_ON_DELIVERY"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;
    @MockitoBean
    private OrderPlacementService orderPlacementService;
    @MockitoBean
    private CustomerOrderService customerOrderService;
    @MockitoBean
    private RestaurantOrderService restaurantOrderService;
    @MockitoBean
    private AdminOrderService adminOrderService;
    @MockitoBean
    private OrderStatsService orderStatsService;
    @MockitoBean
    private MenuItemRevenueService menuItemRevenueService;
    @MockitoBean
    private CustomerTopFoodsService customerTopFoodsService;
    @MockitoBean
    private CustomerInsightsService customerInsightsService;

    @Test
    void aCustomersDashboardIsTheirsAloneAndOnlyForCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/orders/summary").with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/activity").with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/favourite-dishes")).andExpect(status().isUnauthorized());
        verifyNoInteractions(customerInsightsService);

        // The customer is the token's, never a parameter
        mockMvc.perform(get("/api/v1/orders/summary").param("customerId", "99").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isOk());
        verify(customerInsightsService).summary(7L);
        mockMvc.perform(get("/api/v1/orders/activity").with(TestTokens.as("7", "CUSTOMER"))
                        .param("fromDate", "2026-10-01").param("toDate", "2026-10-07"))
                .andExpect(status().isOk());
        verify(customerInsightsService).activity(7L, LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-07"));
        mockMvc.perform(get("/api/v1/orders/favourite-dishes").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isOk());
        verify(customerInsightsService).favouriteDishes(7L, 6);
    }

    @Test
    void theDashboardChecksItsParameters() throws Exception {
        mockMvc.perform(get("/api/v1/orders/favourite-dishes").param("limit", "21")
                        .with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/orders/activity").param("fromDate", "not-a-date")
                        .with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(customerInsightsService);
    }

    @Test
    void withoutATokenTheAnswerIs401AsAProblem() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(customerOrderService);
    }

    @Test
    void identityHeadersAloneNameNobody() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders").header("X-User-Id", "1").header("X-User-Role", "PLATFORM_ADMIN"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminOrderService);
    }

    @Test
    void onlyCustomersHaveACart() throws Exception {
        mockMvc.perform(get("/api/v1/cart").with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void placingAnOrderRequiresAnIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderPlacementService);
    }

    @Test
    void idempotencyKeyMustLookLikeAKey() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(TestTokens.as("7", "CUSTOMER"))
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors['Idempotency-Key']").exists());

        verifyNoInteractions(orderPlacementService);
    }

    @Test
    void invalidCheckoutListsEveryProblem() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(TestTokens.as("7", "CUSTOMER"))
                        .header("Idempotency-Key", "checkout-0001")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.addressId").exists())
                .andExpect(jsonPath("$.errors.paymentMethod").exists());
    }

    @Test
    void customersCannotUseTheRestaurantOrAdminAreas() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/mine/10/orders").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/orders").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restaurantOrderService, adminOrderService);
    }

    /**
     * The order actions of the admin's order list (cancel, history, timeline): platform admins only, whatever the
     * screen shows.
     */
    @Test
    void noRoleButPlatformAdminMayCancelAnOrderOrReadItsHistory() throws Exception {
        for (String role : new String[] {"CUSTOMER", "RESTAURANT_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(put("/api/v1/admin/orders/5/cancel")
                            .with(TestTokens.as("7", role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Changed my mind\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/orders/5/audit-logs").with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/orders/5/timeline").with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/v1/admin/orders/5/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"No identity\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminOrderService);
    }

    @Test
    void onlyTheRestaurantOwnerRoleReadsTheTopItems() throws Exception {
        for (String role : new String[] {"CUSTOMER", "PLATFORM_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(get("/api/v1/restaurants/mine/10/orders/top-items")
                            .with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/restaurants/mine/10/orders/top-items")).andExpect(status().isUnauthorized());
        verifyNoInteractions(restaurantOrderService);

        mockMvc.perform(get("/api/v1/restaurants/mine/10/orders/top-items").param("from", "2026-10-01")
                        .param("to", "2026-10-07").param("limit", "20")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isOk());
        verify(restaurantOrderService).getTopItems(3L, 10L, LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-07"), 20);
        mockMvc.perform(get("/api/v1/restaurants/mine/10/orders/top-items")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isOk());
        verify(restaurantOrderService).getTopItems(3L, 10L, null, null, 5);
    }

    @Test
    void topItemsLimitMustBeBetween1And20() throws Exception {
        for (String limit : new String[] {"0", "21", "x"}) {
            mockMvc.perform(get("/api/v1/restaurants/mine/10/orders/top-items").param("limit", limit)
                            .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/restaurants/mine/10/orders/top-items").param("from", "07-10-2026")
                        .with(TestTokens.as("3", "RESTAURANT_ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(restaurantOrderService);
    }

    @Test
    void onlyAPlatformAdminReadsTheRevenueOfADish() throws Exception {
        for (String role : new String[] {"CUSTOMER", "RESTAURANT_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(get("/api/v1/admin/orders/stats/menu-items/9/revenue")
                            .with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin/orders/stats/menu-items/9/revenue"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(menuItemRevenueService);

        mockMvc.perform(get("/api/v1/admin/orders/stats/menu-items/9/revenue").param("period", "WEEKLY")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
        verify(menuItemRevenueService).getRevenue(9L, RevenuePeriod.WEEKLY);

        mockMvc.perform(get("/api/v1/admin/orders/stats/menu-items/9/revenue").param("period", "YEARLY")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAPlatformAdminReadsTheTopFoodsOfACustomer() throws Exception {
        String path = "/api/v1/admin/orders/stats/customers/7/top-foods";
        for (String role : new String[] {"CUSTOMER", "RESTAURANT_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(get(path).with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(customerTopFoodsService);

        mockMvc.perform(get(path).with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
        verify(customerTopFoodsService).getTopFoods(7L, TopFoodsPeriod.MONTHLY, 5);

        mockMvc.perform(get(path).param("period", "DAILY").param("limit", "20")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
        verify(customerTopFoodsService).getTopFoods(7L, TopFoodsPeriod.DAILY, 20);

        for (String limit : new String[] {"0", "21"}) {
            mockMvc.perform(get(path).param("limit", limit)
                            .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(path).param("period", "YEARLY")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAdminMustGiveAReasonToCancelAnOrder() throws Exception {
        mockMvc.perform(put("/api/v1/admin/orders/5/cancel")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());

        verifyNoInteractions(adminOrderService);
    }

    @Test
    void anAdminsCancelReachesTheServiceWhichAppliesTheStatusRules() throws Exception {
        mockMvc.perform(put("/api/v1/admin/orders/5/cancel")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Customer support: duplicate order\"}"))
                .andExpect(status().isOk());

        verify(adminOrderService).cancelOrder(eq(5L), eq("Customer support: duplicate order"), any());
    }
}
