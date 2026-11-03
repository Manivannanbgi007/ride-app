package com.fooddelivery.aimcpserver.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MOCKED: the admin tools against a stubbed gateway (no model, no network beyond localhost). */
class AdminToolsMockedTest {

    private StubGateway gateway;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore());
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    @Test
    void mockedOrderStatsMapsTheRealResponseAndBuildsAStatsCard() {
        gateway.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_order_stats", "{}");

        assertThat(result).contains("\"orders\":12", "\"delivered\":7", "\"cancelledOrRejected\":3",
                "\"revenueOfDeliveredOrders\":\"4300.5\"", "\"byStatus\"");
        assertThat(result).doesNotContain("perDay");
        assertThat(run.cards()).hasSize(1);
        assertThat(run.cards().get(0)).isInstanceOf(Card.Stats.class);
        assertThat(((Card.Stats) run.cards().get(0)).stats()).extracting(Card.Stat::label)
                .contains("Orders", "Delivered", "Revenue");
    }

    @Test
    void mockedOrderStatsWithoutDatesAsksForTodayInTheBusinessTimeZoneAndForwardsTheIdentity() {
        gateway.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);

        ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats", "{}");

        StubGateway.Call call = gateway.calls().get(0);
        assertThat(call.query()).contains("fromDate=2026-10-09", "toDate=2026-10-09");
        assertThat(call.header("Authorization")).isEqualTo("Bearer token-of-user-2");
        assertThat(call.header("X-Request-Id")).isEqualTo("req-admin");
        // The service token is ours: it is never sent to the business APIs
        assertThat(call.header("X-Service-Token")).isNull();
    }

    @Test
    void mockedOrderStatsRejectsBadDatesAndTooLongPeriodsWithoutCallingTheGateway() {
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats", "{\"fromDate\":\"09/10/2026\"}"))
                .startsWith("ERROR").contains("yyyy-MM-dd");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats",
                "{\"fromDate\":\"2025-01-01\",\"toDate\":\"2026-10-01\"}")).contains("at most 366 days");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats",
                "{\"fromDate\":\"2026-10-05\",\"toDate\":\"2026-10-01\"}")).contains("must not be before");

        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedListOrdersAddsCancellationReasonsAndStripsMarkupFromThem() {
        gateway.on("GET", "/api/v1/admin/orders", 200, Responses.ORDERS_PAGE);
        gateway.on("GET", "/api/v1/admin/orders/101", 200, Responses.ORDER_CANCELLED_DETAIL);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_list_orders",
                "{\"status\":\"cancelled\",\"limit\":5}");

        assertThat(result).contains("\"totalFound\":2", "Customer changed their mind", "\"restaurant\":\"Spice Hub\"");
        assertThat(result).doesNotContain("<b>").doesNotContain("</b>");
        assertThat(gateway.calls().get(0).query()).contains("status=CANCELLED", "size=20", "sort=createdAt%2Cdesc");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.entityType()).isEqualTo("ORDER");
        assertThat(card.items().get(0).link().path()).isEqualTo("/admin/orders/101");
        assertThat(card.items().get(0).badges()).containsExactly("CANCELLED");
    }

    @Test
    void mockedListOrdersValidatesStatusLimitAndIds() {
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, "admin_list_orders", "{\"status\":\"BANANA\"}"))
                .contains("status must be one of", "DELIVERED");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, "admin_list_orders", "{\"limit\":500}"))
                .contains("limit must be between 1 and 25");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, "admin_list_orders", "{\"restaurantId\":-4}"))
                .contains("restaurantId must be a positive number");

        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedTopRestaurantsCanRankByOrdersAndLinksToTheAdminRestaurantPage() {
        gateway.on("GET", "/api/v1/admin/orders/stats/restaurants", 200, Responses.RESTAURANT_PERFORMANCE);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_top_restaurants",
                "{\"rankBy\":\"ORDERS\",\"limit\":2}");

        // Biryani House has 9 orders, Spice Hub 5: ranked by orders it comes first although it earned less
        assertThat(result.indexOf("Biryani House")).isLessThan(result.indexOf("Spice Hub"));
        assertThat(gateway.calls().get(0).query()).contains("limit=100");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.items().get(0).link().path()).isEqualTo("/admin/restaurants/8");
    }

    @Test
    void mockedDeliveryToolsMapTheRealShapes() {
        gateway.on("GET", "/api/v1/admin/deliveries/summary", 200, Responses.DELIVERY_REPORT);
        gateway.on("GET", "/api/v1/admin/deliveries/delayed", 200, Responses.DELAYED_PAGE);
        var run = ToolRunner.newRun();

        String summary = ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_delivery_summary", "{}");
        String delayed = ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_delayed_deliveries", "{}");

        assertThat(summary).contains("\"delivered\":6", "\"averageDeliveryMinutes\":31.5", "\"delayedNow\":2");
        assertThat(delayed).contains("\"totalDelayed\":1", "\"minutesSinceReady\":42", "\"orderId\":104");
        Card.Entities card = (Card.Entities) run.cards().get(1);
        assertThat(card.items().get(0).meta()).extracting(Card.Meta::value).contains("not assigned", "42 min");
        assertThat(card.items().get(0).link().path()).isEqualTo("/admin/deliveries/55");
    }

    @Test
    void mockedPendingRestaurantsAsksForStatusPendingOldestFirst() {
        gateway.on("GET", "/api/v1/admin/restaurants", 200, Responses.PENDING_RESTAURANTS);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_pending_restaurants", "{}");

        assertThat(result).contains("\"totalPending\":1", "New Dosa Corner");
        assertThat(gateway.calls().get(0).query()).contains("status=PENDING", "sort=createdAt%2Casc");
        assertThat(((Card.Entities) run.cards().get(0)).items().get(0).link().path()).isEqualTo("/admin/restaurants/21");
    }

    @Test
    void mockedAdminToolsAreRefusedForOtherRolesWithoutCallingTheGateway() {
        for (var caller : List.of(Fixtures.CUSTOMER, Fixtures.OWNER)) {
            for (String tool : List.of("admin_order_stats", "admin_list_orders", "admin_top_restaurants",
                    "admin_delivery_summary", "admin_delayed_deliveries", "admin_pending_restaurants")) {
                assertThat(ToolRunner.call(stack, caller, tool, "{}")).startsWith("ERROR")
                        .contains("not available for your role");
            }
        }

        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedAdminToolsTheMethodsRecheckTheRoleThemselvesToo() {
        // Bypassing the guard (direct call): the tool itself still refuses a customer identity
        var context = new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                com.fooddelivery.aimcpserver.security.CallerContext.TOOL_CONTEXT_KEY, Fixtures.CUSTOMER));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stack.adminOrders().adminOrderStats(null, null, context))
                .isInstanceOf(com.fooddelivery.aimcpserver.support.ToolException.class);
        assertThat(gateway.calls()).isEmpty();
    }
}
