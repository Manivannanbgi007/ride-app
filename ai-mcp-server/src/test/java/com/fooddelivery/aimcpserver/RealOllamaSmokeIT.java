package com.fooddelivery.aimcpserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fooddelivery.aimcpserver.action.ActionStore;
import com.fooddelivery.aimcpserver.agent.AgentService;
import com.fooddelivery.aimcpserver.dto.ChatRequest;
import com.fooddelivery.aimcpserver.dto.ChatResponse;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryActionStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * OPT-IN smoke test with the REAL model (not MOCKED): a real Ollama answers real questions, the business APIs are a
 * stubbed gateway and prepared changes are kept in memory (no Redis needed). Needs Ollama running with the model
 * installed:
 * <pre>mvnw -o test -Dtest=RealOllamaSmokeIT -Dai.real=true</pre>
 * (OLLAMA_BASE_URL / OLLAMA_MODEL as for the service). Without -Dai.real=true it is skipped, so the normal build
 * never needs Ollama. Real model answers vary: the checks are about the tool path and the prepared button, not the
 * wording. The owner here has two restaurants: the case where a small model used to ask "which restaurant?" or
 * "shall I...?" instead of preparing the change.
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "ai.real", matches = "true")
@Import(RealOllamaSmokeIT.InMemoryStores.class)
class RealOllamaSmokeIT {

    private static final StubGateway GATEWAY = new StubGateway();
    private static final String ORDER = """
            {"id":%d,"customerId":1,"customerName":"Priya Sharma","restaurantId":7,"restaurantName":"Spice Hub",
             "status":"%s","paymentMethod":"UPI","paymentStatus":"PAID",
             "items":[{"name":"Chicken Biryani","quantity":2}],"price":{"totalAmount":562.90,"currency":"INR"}}
            """;

    @TestConfiguration
    static class InMemoryStores {

        @Bean
        @Primary
        ActionStore inMemoryActionStore() {
            return new InMemoryActionStore();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.gateway.base-url", GATEWAY::url);
        registry.add("management.otlp.metrics.export.enabled", () -> "false");
        registry.add("management.opentelemetry.tracing.export.otlp.enabled", () -> "false");
        registry.add("management.tracing.export.otlp.enabled", () -> "false");
    }

    @Autowired
    private AgentService agent;

    @BeforeAll
    static void ownerData() {
        GATEWAY.on("GET", "/api/v1/restaurants/mine", 200,
                "[{\"id\":5,\"name\":\"Dosa Point\"},{\"id\":7,\"name\":\"Spice Hub\"}]");
        GATEWAY.on("GET", "/api/v1/restaurants/mine/7/orders/42", 200, ORDER.formatted(42, "PLACED"));
        GATEWAY.on("GET", "/api/v1/restaurants/mine/7/orders/43", 200, ORDER.formatted(43, "PREPARING"));
        GATEWAY.on("GET", "/api/v1/restaurants/mine/7/menu-items", 200,
                "[{\"id\":9,\"name\":\"Paneer Tikka\",\"price\":220.00,\"available\":true}]");
        GATEWAY.on("GET", "/api/v1/restaurants/mine/5/menu-items", 200,
                "[{\"id\":3,\"name\":\"Masala Dosa\",\"price\":120.00,\"available\":true}]");
    }

    @AfterAll
    static void stop() {
        GATEWAY.close();
    }

    @Test
    void adminQuestionRealModelCallsTheStatsToolAndAnswersFromItsNumbers() {
        GATEWAY.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);

        ChatResponse response = ask(Fixtures.ADMIN, "How many orders were delivered today?");

        assertThat(response.toolsUsed()).contains("admin_order_stats");
        assertThat(response.cards()).isNotEmpty();
        assertThat(response.text()).isNotBlank();
        // The stub says 7 delivered orders: a model that used the tool result mentions the number
        assertThat(response.text()).contains("7");
    }

    @Test
    void ownerAcceptRealModelPreparesTheButtonWithoutAskingFirst() {
        ChatResponse response = ask(Fixtures.OWNER, "Accept order 42");

        assertThat(response.pendingAction()).as(response.text()).isNotNull();
        assertThat(response.pendingAction().kind()).isEqualTo("ACCEPT_ORDER");
        assertNothingChanged();
    }

    @Test
    void ownerReadyRealModelPreparesTheButton() {
        ChatResponse response = ask(Fixtures.OWNER, "Order 43 is ready");

        assertThat(response.pendingAction()).as(response.text()).isNotNull();
        assertThat(response.pendingAction().kind()).isEqualTo("MARK_READY");
        assertNothingChanged();
    }

    @Test
    void ownerRejectWithAReasonRealModelPreparesTheButton() {
        ChatResponse response = ask(Fixtures.OWNER, "Reject order 42, we ran out of chicken");

        assertThat(response.pendingAction()).as(response.text()).isNotNull();
        assertThat(response.pendingAction().kind()).isEqualTo("REJECT_ORDER");
        assertNothingChanged();
    }

    @Test
    void ownerSoldOutRealModelPreparesTheButtonWithoutAskingWhichRestaurant() {
        ChatResponse response = ask(Fixtures.OWNER, "Paneer Tikka is sold out");

        assertThat(response.pendingAction()).as(response.text()).isNotNull();
        assertThat(response.pendingAction().kind()).isEqualTo("SET_ITEM_AVAILABILITY");
        assertNothingChanged();
    }

    @Test
    void ownerReportQuestionRealModelPreparesNothing() {
        ChatResponse response = ask(Fixtures.OWNER, "How many orders did I accept today?");

        assertThat(response.pendingAction()).isNull();
        assertNothingChanged();
    }

    @Test
    void partnerPickupRealModelStillPreparesTheButton() {
        GATEWAY.on("GET", "/api/v1/delivery/deliveries/mine/current", 200,
                "{\"id\":31,\"orderId\":42,\"status\":\"ACCEPTED\",\"restaurantName\":\"Spice Hub\",\"cashToCollect\":0}");

        ChatResponse response = ask(Fixtures.PARTNER, "I picked up the food");

        assertThat(response.pendingAction()).as(response.text()).isNotNull();
        assertThat(response.pendingAction().kind()).isEqualTo("MARK_PICKED_UP");
        assertNothingChanged();
    }

    private ChatResponse ask(CallerContext caller, String question) {
        ChatResponse response = agent.chat(caller, new ChatRequest(question, List.of()));
        log.info("REAL MODEL role={} question=\"{}\" tools={} toolCalls={} pending={} durationMs={} answer=\"{}\"",
                caller.role(), question, response.toolsUsed(), response.toolCalls(),
                response.pendingAction() == null ? "none" : response.pendingAction().kind(), response.durationMs(),
                response.text());
        return response;
    }

    // Preparing never changes anything: only GET requests reach the platform
    private static void assertNothingChanged() {
        assertThat(GATEWAY.calls()).allSatisfy(call -> assertThat(call.method()).isEqualTo("GET"));
    }
}
