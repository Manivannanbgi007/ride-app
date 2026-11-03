package com.fooddelivery.aimcpserver.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.json.JsonMapper;

/** MOCKED: what the guard around every tool does (timeout, errors, size cap, budget, metrics, no caller). */
class GuardedToolMockedTest {

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
    void mockedTimeoutASlowDataSourceBecomesAShortMessageNotAHang() {
        JsonMapper mapper = JsonMapper.builder().build();
        AppProperties base = Fixtures.properties(gateway.url());
        AppProperties fast = new AppProperties(base.serviceToken(), base.timezone(), base.gateway(), base.ollama(),
                new AppProperties.Agent(6, Duration.ofSeconds(30), Duration.ofMillis(300), 12, 6000, 6000),
                base.proposal());
        GatewayClient client = Fixtures.gatewayClient(gateway.url(), mapper);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        var resolver = Fixtures.callerResolver();
        var tools = new AdminOrderTools(client, resolver, Fixtures.clock(), mapper);
        var catalog = new ToolCatalog(List.of(tools), fast, executor, resolver, stack.registry());
        gateway.on("GET", "/api/v1/admin/orders/stats", call -> {
            sleep(1500);
            return new StubGateway.Reply(200, Responses.ADMIN_STATS);
        });
        long start = System.nanoTime();

        String result = catalog.all().stream().filter(t -> t.getToolDefinition().name().equals("admin_order_stats")).findFirst()
                .orElseThrow().call("{}", new ToolContext(Map.of(CallerContext.TOOL_CONTEXT_KEY, Fixtures.ADMIN)));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1200));
        assertThat(result).startsWith("ERROR").contains("took too long");
        assertThat(stack.registry().get("ai.tool.calls").tag("outcome", "timeout").counter().count()).isEqualTo(1);
    }

    @Test
    void mockedGatewayFailureIsExplainedWithoutHostsOrStackTraces() {
        gateway.on("GET", "/api/v1/admin/orders/stats", 500, "{\"trace\":\"java.lang.NullPointerException at x.y.Z\"}");

        String result = ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats", "{}");

        assertThat(result).startsWith("ERROR").contains("temporarily unavailable");
        assertThat(result).doesNotContain("NullPointer").doesNotContain("127.0.0.1").doesNotContain("Exception");
    }

    @Test
    void mockedGatewayUnreachableIsAnUnderstandableMessage() throws Exception {
        String url = gateway.url();
        gateway.close();
        Fixtures.Stack down = Fixtures.stack(url, Fixtures.clock(), new InMemoryProposalStore());

        String result = ToolRunner.call(down, Fixtures.ADMIN, "admin_order_stats", "{}");

        assertThat(result).startsWith("ERROR").contains("not reachable right now");
        gateway = new StubGateway();
    }

    @Test
    void mockedIdempotentGetsAreRetriedOnceWhenTheGatewayIsBriefly503() {
        List<Integer> answers = new ArrayList<>(List.of(503, 200));
        gateway.on("GET", "/api/v1/admin/orders/stats", call -> new StubGateway.Reply(answers.remove(0),
                "{\"orders\":1}"));

        String result = ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats", "{}");

        assertThat(result).doesNotStartWith("ERROR").contains("\"orders\":1");
        assertThat(gateway.count("GET", "/api/v1/admin/orders/stats")).isEqualTo(2);
    }

    @Test
    void mockedLargeResultsAreCutToTheCapWithAMoreMarker() {
        StringBuilder orders = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            orders.append(i > 1 ? "," : "").append("{\"id\":").append(i)
                    .append(",\"restaurantName\":\"").append("Very Long Restaurant Name ".repeat(8))
                    .append("\",\"status\":\"PLACED\",\"paymentMethod\":\"UPI\",\"paymentStatus\":\"PAID\","
                            + "\"totalAmount\":100,\"itemCount\":1,\"createdAt\":\"2026-10-09T05:10:00Z\"}");
        }
        gateway.on("GET", "/api/v1/admin/orders", 200, "{\"content\":[" + orders + "],\"totalElements\":25}");
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_list_orders", "{\"limit\":25}");

        assertThat(result.length()).isLessThanOrEqualTo(6000);
        assertThat(result).contains("\"more\":").contains("more not shown").contains("\"totalFound\":25");
        // The card still carries up to 25 items: cards come from data, not from what the model reads
        assertThat(((com.fooddelivery.aimcpserver.dto.Card.Entities) run.cards().get(0)).items()).hasSize(25);
    }

    @Test
    void mockedMetricsCountEveryCallByToolAndOutcome() {
        gateway.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);

        ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats", "{}");
        ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats", "{\"fromDate\":\"x\"}");
        ToolRunner.call(stack, Fixtures.CUSTOMER, "admin_order_stats", "{}");

        var registry = stack.registry();
        assertThat(registry.get("ai.tool.calls").tags("tool", "admin_order_stats", "outcome", "ok").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("ai.tool.calls").tags("tool", "admin_order_stats", "outcome", "invalid").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("ai.tool.calls").tags("tool", "admin_order_stats", "outcome", "forbidden").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("ai.tool.duration").tag("tool", "admin_order_stats").timer().count()).isEqualTo(3);
    }

    @Test
    void mockedToolBudgetAfterTheLimitTheModelIsToldToAnswerAndAStubbornModelIsCutOff() {
        gateway.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);
        var run = new AgentRun(2, System.nanoTime() + Duration.ofSeconds(30).toNanos());

        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_order_stats", "{}")).doesNotStartWith("ERROR");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_order_stats", "{}")).doesNotStartWith("ERROR");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_order_stats", "{}")).startsWith("TOOL LIMIT REACHED");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_order_stats", "{}")).startsWith("TOOL LIMIT REACHED");
        assertThatThrownBy(() -> ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_order_stats", "{}"))
                .isInstanceOf(AgentAbortedException.class);

        assertThat(gateway.count("GET", "/api/v1/admin/orders/stats")).isEqualTo(2);
        assertThat(run.toolCalls()).isEqualTo(2);
    }

    @Test
    void mockedNoCallerACallWithoutIdentityGetsAnAuthenticationErrorNeverData() {
        var callback = stack.catalog().all().get(0);

        String result = callback.call("{}", new ToolContext(Map.of()));

        assertThat(result).startsWith("ERROR").contains("Authentication required");
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedRolesEachRoleSeesExactlyItsOwnTools() {
        var catalog = stack.catalog();

        assertThat(catalog.namesForRole(com.fooddelivery.aimcpserver.security.Role.PLATFORM_ADMIN)).containsExactlyInAnyOrder(
                "admin_order_stats", "admin_list_orders", "admin_top_restaurants", "admin_delivery_summary",
                "admin_delayed_deliveries", "admin_pending_restaurants", "admin_prepare_cancel_order",
                "admin_prepare_reassign_delivery");
        assertThat(catalog.namesForRole(com.fooddelivery.aimcpserver.security.Role.RESTAURANT_ADMIN))
                .containsExactlyInAnyOrder("owner_my_restaurants", "owner_sales_summary", "owner_orders",
                        "owner_top_items", "owner_delivery_partners", "owner_menu", "current_offers",
                        "owner_prepare_order_step", "owner_prepare_item_availability");
        assertThat(catalog.namesForRole(com.fooddelivery.aimcpserver.security.Role.CUSTOMER)).containsExactlyInAnyOrder(
                "search_restaurants", "search_foods", "restaurant_menu", "current_offers", "my_addresses", "my_orders",
                "my_order_status", "prepare_order", "prepare_cancel_order");
        assertThat(catalog.namesForRole(com.fooddelivery.aimcpserver.security.Role.DELIVERY_PARTNER))
                .containsExactlyInAnyOrder("partner_current_delivery", "partner_delivery_details",
                        "partner_my_deliveries", "partner_my_stats", "partner_prepare_delivery_step");
        assertThat(catalog.all()).hasSize(8 + 8 + 1 + 8 + 5);
    }

    @Test
    void mockedToolDefinitionsDoNotExposeTheCallerOrTheContextAsArguments() {
        for (var tool : stack.catalog().all()) {
            String schema = tool.getToolDefinition().inputSchema();
            assertThat(schema).doesNotContain("userId").doesNotContain("toolContext").doesNotContain("role")
                    .doesNotContain("caller");
            assertThat(tool.getToolDefinition().description()).isNotBlank();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
