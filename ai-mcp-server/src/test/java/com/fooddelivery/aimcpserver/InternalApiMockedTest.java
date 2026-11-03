package com.fooddelivery.aimcpserver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fooddelivery.aimcpserver.testsupport.ContextTestSupport;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel.Call;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel.Fail;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel.Say;
import com.fooddelivery.aimcpserver.testsupport.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

/** MOCKED: the HTTP surface (service token, access token, validation, error shapes) in a full Spring context. */
@AutoConfigureMockMvc
class InternalApiMockedTest extends ContextTestSupport {

    private static final String TOKEN = Fixtures.TOKEN;
    /** The customer's access token, as ai-mcp-client forwards it */
    private static final String CUSTOMER_TOKEN = TestJwts.token(1, "CUSTOMER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptedChatModel model;

    @BeforeEach
    void reset() {
        GATEWAY.reset();
        model.script();
    }

    private static MockHttpServletRequestBuilder chat(String body) {
        return post("/internal/agent/chat").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder request) {
        return request.header("X-Service-Token", TOKEN).header("Authorization", "Bearer " + CUSTOMER_TOKEN)
                .header("X-Request-Id", "ctx-test-1");
    }

    @Test
    void mockedServiceTokenMissingOrWrongIs401OnEveryInternalEndpointAndNothingRuns() throws Exception {
        mockMvc.perform(chat("{\"message\":\"hi\"}").header("Authorization", TestJwts.bearer(1, "CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").exists());
        mockMvc.perform(chat("{\"message\":\"hi\"}").header("X-Service-Token", "wrong-token")
                        .header("Authorization", TestJwts.bearer(1, "CUSTOMER")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(chat("{\"message\":\"hi\"}").header("X-Service-Token", "")
                        .header("Authorization", TestJwts.bearer(1, "CUSTOMER")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/agent/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/agent/actions/" + UUID.randomUUID() + "/confirm")
                .header("Authorization", TestJwts.bearer(1, "CUSTOMER"))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/agent/actions/" + UUID.randomUUID() + "/cancel")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mockedServiceTokenTheTokenIsNotNeededForHealthButIsForEverythingUnderInternal() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void mockedHealthReadinessDoesNotDependOnOllamaOrRedis() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void mockedStatusReportsUnavailableWhenOllamaIsNotRunning() throws Exception {
        mockMvc.perform(get("/internal/agent/status").header("X-Service-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void mockedChatReturnsTheDocumentedShape() throws Exception {
        GATEWAY.on("GET", "/api/v1/search/foods", 200, Responses.SEARCH_FOODS);
        model.script(new Call("search_foods", "{\"query\":\"biryani\"}"), new Say("Found one dish."));

        mockMvc.perform(asCustomer(chat("{\"message\":\"find biryani\",\"history\":["
                        + "{\"role\":\"USER\",\"text\":\"hi\"},{\"role\":\"ASSISTANT\",\"text\":\"hello\"}]}")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "ctx-test-1"))
                .andExpect(jsonPath("$.text").value("Found one dish."))
                .andExpect(jsonPath("$.cards[0].kind").value("entities"))
                .andExpect(jsonPath("$.cards[0].entityType").value("FOOD"))
                .andExpect(jsonPath("$.cards[0].items[0].title").value("Chicken Biryani"))
                .andExpect(jsonPath("$.cards[0].items[0].link.path").value("/restaurants/7"))
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.pendingAction").doesNotExist())
                .andExpect(jsonPath("$.toolsUsed[0]").value("search_foods"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.toolCalls").value(1));
        // The caller's own token was forwarded to the business API, and nothing else says who the caller is
        org.assertj.core.api.Assertions.assertThat(GATEWAY.calls().get(0).header("Authorization"))
                .isEqualTo("Bearer " + CUSTOMER_TOKEN);
        org.assertj.core.api.Assertions.assertThat(GATEWAY.calls().get(0).header("X-User-Id")).isNull();
        org.assertj.core.api.Assertions.assertThat(GATEWAY.calls().get(0).header("X-Request-Id")).isEqualTo("ctx-test-1");
    }

    @Test
    void mockedChatValidatesTheRequest() throws Exception {
        mockMvc.perform(asCustomer(chat("{\"message\":\"\"}"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.message").exists());
        mockMvc.perform(asCustomer(chat("{\"message\":\"" + "x".repeat(1001) + "\"}"))).andExpect(status().isBadRequest());
        mockMvc.perform(asCustomer(chat("{\"message\":\"hi\",\"history\":[{\"role\":\"USER\",\"text\":\""
                + "y".repeat(2001) + "\"}]}"))).andExpect(status().isBadRequest());
        mockMvc.perform(asCustomer(chat("{\"message\":\"hi\",\"history\":[{\"role\":\"SYSTEM\",\"text\":\"x\"}]}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asCustomer(chat("not json"))).andExpect(status().isBadRequest());
    }

    @Test
    void mockedChatALongHistoryIsAcceptedAndTrimmed() throws Exception {
        model.script(new Say("ok"));
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            history.append(i > 0 ? "," : "").append("{\"role\":\"USER\",\"text\":\"turn ").append(i).append("\"}");
        }

        mockMvc.perform(asCustomer(chat("{\"message\":\"hi\",\"history\":[" + history + "]}")))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(model.prompts().get(0).getInstructions()).hasSize(14);
    }

    @Test
    void mockedChatWithoutAValidAccessTokenIs401AndNothingRuns() throws Exception {
        mockMvc.perform(chat("{\"message\":\"hi\"}").header("X-Service-Token", TOKEN)).andExpect(status().isUnauthorized());
        // Identity headers alone name nobody any more
        mockMvc.perform(chat("{\"message\":\"hi\"}").header("X-Service-Token", TOKEN)
                .header("X-User-Id", "1").header("X-User-Role", "CUSTOMER")).andExpect(status().isUnauthorized());
        java.util.List<String> refused = java.util.List.of(
                TestJwts.tokenSignedByAnotherKey(1, "CUSTOMER"),
                TestJwts.token(1, "CUSTOMER", claims -> claims.issuedAt(java.time.Instant.now().minusSeconds(960))
                        .expiresAt(java.time.Instant.now().minusSeconds(300))),
                TestJwts.token(1, "CUSTOMER", claims -> claims.audience(java.util.List.of("another-api"))),
                TestJwts.token(1, "CUSTOMER", claims -> claims.issuer("https://somebody.else")),
                TestJwts.token(1, "SUPERUSER"),
                TestJwts.token(1, "CUSTOMER", claims -> claims.subject("abc")),
                "not.a.token");
        for (String token : refused) {
            mockMvc.perform(chat("{\"message\":\"hi\"}").header("X-Service-Token", TOKEN)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Authentication required"));
        }
        org.assertj.core.api.Assertions.assertThat(model.prompts()).isEmpty();
    }

    @Test
    void mockedChatDeliveryPartnersAreAnsweredWithOnlyTheirOwnTools() throws Exception {
        model.script(new Say("You have no delivery right now."));

        mockMvc.perform(chat("{\"message\":\"which delivery is mine?\"}").header("X-Service-Token", TOKEN)
                        .header("Authorization", TestJwts.bearer(8, "DELIVERY_PARTNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("You have no delivery right now."));

        org.assertj.core.api.Assertions.assertThat(model.lastOfferedTools()).containsExactlyInAnyOrder(
                "partner_current_delivery", "partner_delivery_details", "partner_my_deliveries", "partner_my_stats",
                "partner_prepare_delivery_step");
    }

    @Test
    void mockedAChangeOfAnyRoleIsConfirmedOnlyByItsOwnUserAndRoleOverHttp() throws Exception {
        GATEWAY.on("GET", "/api/v1/delivery/deliveries/mine/current", 200,
                "{\"id\":31,\"orderId\":42,\"status\":\"ACCEPTED\",\"restaurantName\":\"Spice Hub\",\"cashToCollect\":0}");
        GATEWAY.on("PUT", "/api/v1/delivery/deliveries/mine/31/pickup", 200, "{\"id\":31,\"status\":\"PICKED_UP\"}");
        model.script(new Call("partner_prepare_delivery_step", "{\"step\":\"PICKED_UP\"}"), new Say("Press Picked up."));

        String body = mockMvc.perform(asPartner(chat("{\"message\":\"I have the food\"}"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction.kind").value("MARK_PICKED_UP"))
                .andExpect(jsonPath("$.pendingAction.summary").doesNotExist())
                .andExpect(jsonPath("$.pendingAction.details.confirmLabel").value("Picked up"))
                .andReturn().getResponse().getContentAsString();
        String actionId = com.jayway.jsonpath.JsonPath.read(body, "$.pendingAction.actionId");
        org.assertj.core.api.Assertions.assertThat(GATEWAY.count("PUT", "/api/v1/delivery/deliveries/mine/31/pickup"))
                .isZero();

        // The same user id under another role, and another partner, are refused
        mockMvc.perform(post("/internal/agent/actions/" + actionId + "/confirm").header("X-Service-Token", TOKEN)
                .header("Authorization", TestJwts.bearer(8, "CUSTOMER"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/agent/actions/" + actionId + "/confirm").header("X-Service-Token", TOKEN)
                .header("Authorization", TestJwts.bearer(9, "DELIVERY_PARTNER"))).andExpect(status().isForbidden());
        mockMvc.perform(asPartner(post("/internal/agent/actions/act_" + UUID.randomUUID() + "/confirm")))
                .andExpect(status().isNotFound());

        mockMvc.perform(asPartner(post("/internal/agent/actions/" + actionId + "/confirm")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.order").doesNotExist())
                .andExpect(jsonPath("$.message").value("Delivery #31 is marked picked up."))
                .andExpect(jsonPath("$.link.path").value("/partner/deliveries/31"));
        mockMvc.perform(asPartner(post("/internal/agent/actions/" + actionId + "/confirm")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ALREADY_DONE"));
        mockMvc.perform(asPartner(post("/internal/agent/actions/" + actionId + "/cancel")))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(GATEWAY.count("PUT", "/api/v1/delivery/deliveries/mine/31/pickup"))
                .isEqualTo(1);
        // An order proposal id is still for customers only
        mockMvc.perform(asPartner(post("/internal/agent/actions/" + UUID.randomUUID() + "/confirm")))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder asPartner(MockHttpServletRequestBuilder request) {
        return request.header("X-Service-Token", TOKEN).header("Authorization", TestJwts.bearer(8, "DELIVERY_PARTNER"));
    }

    @Test
    void mockedChatOllamaDownIs503InTheSiblingsErrorShape() throws Exception {
        model.script(new Fail(new ResourceAccessException("I/O error", new java.net.ConnectException("refused"))));

        mockMvc.perform(asCustomer(chat("{\"message\":\"hi\"}")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Assistant unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The AI model is not running right now. Please try again later."))
                .andExpect(jsonPath("$.requestId").value("ctx-test-1"));
    }

    @Test
    void mockedActionsPrepareConfirmTwiceAndCancelOverHttp() throws Exception {
        GATEWAY.on("GET", "/api/v1/restaurants", 200, Responses.RESTAURANT_LIST);
        GATEWAY.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_DETAILS);
        GATEWAY.on("GET", "/api/v1/restaurants/7/menu", 200, Responses.MENU);
        GATEWAY.on("GET", "/api/v1/users/me/addresses", 200, Responses.ADDRESSES);
        GATEWAY.on("PUT", "/api/v1/cart/items/31", 200, Responses.PRICED_CART);
        GATEWAY.on("DELETE", "/api/v1/cart", 204, null);
        GATEWAY.on("POST", "/api/v1/orders", 201, Responses.ORDER_CREATED);
        java.util.List<String> carts = new java.util.ArrayList<>(java.util.List.of(Responses.EMPTY_CART, Responses.PRICED_CART));
        GATEWAY.on("GET", "/api/v1/cart", call -> new com.fooddelivery.aimcpserver.testsupport.StubGateway.Reply(200,
                carts.size() > 1 ? carts.remove(0) : carts.get(0)));
        model.script(new Call("prepare_order", "{\"restaurant\":\"Spice Hub\","
                + "\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}],"
                + "\"address\":\"5\",\"paymentMethod\":\"CASH_ON_DELIVERY\"}"), new Say("Please confirm."));

        String body = mockMvc.perform(asCustomer(chat("{\"message\":\"order\"}"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction.kind").value("PLACE_ORDER"))
                .andExpect(jsonPath("$.pendingAction.summary.total").value(460.00))
                .andExpect(jsonPath("$.pendingAction.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String actionId = com.jayway.jsonpath.JsonPath.read(body, "$.pendingAction.actionId");

        mockMvc.perform(asCustomer(post("/internal/agent/actions/" + actionId + "/confirm")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.order.id").value(777)).andExpect(jsonPath("$.link.path").value("/orders/777"));
        mockMvc.perform(asCustomer(post("/internal/agent/actions/" + actionId + "/confirm")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ALREADY_CREATED"))
                .andExpect(jsonPath("$.order.id").value(777));
        mockMvc.perform(asCustomer(post("/internal/agent/actions/" + actionId + "/cancel")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/internal/agent/actions/" + actionId + "/confirm").header("X-Service-Token", TOKEN)
                        .header("Authorization", TestJwts.bearer(99, "CUSTOMER"))).andExpect(status().isForbidden());
        mockMvc.perform(asCustomer(post("/internal/agent/actions/" + UUID.randomUUID() + "/confirm")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/agent/actions/" + actionId + "/confirm").header("X-Service-Token", TOKEN)
                        .header("Authorization", TestJwts.bearer(2, "PLATFORM_ADMIN"))).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(GATEWAY.count("POST", "/api/v1/orders")).isEqualTo(1);
    }

    @Test
    void mockedActionsCancelReturns204() throws Exception {
        GATEWAY.on("GET", "/api/v1/restaurants", 200, Responses.RESTAURANT_LIST);
        GATEWAY.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_DETAILS);
        GATEWAY.on("GET", "/api/v1/restaurants/7/menu", 200, Responses.MENU);
        GATEWAY.on("GET", "/api/v1/users/me/addresses", 200, Responses.ADDRESSES);
        GATEWAY.on("GET", "/api/v1/cart", 200, Responses.FULL_CART);
        model.script(new Call("prepare_order", "{\"restaurant\":\"Spice Hub\","
                + "\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":1}],"
                + "\"address\":\"5\",\"paymentMethod\":\"UPI\"}"), new Say("Please confirm."));
        String body = mockMvc.perform(asCustomer(chat("{\"message\":\"order\"}"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String actionId = com.jayway.jsonpath.JsonPath.read(body, "$.pendingAction.actionId");

        mockMvc.perform(asCustomer(post("/internal/agent/actions/" + actionId + "/cancel"))).andExpect(status().isNoContent());
        mockMvc.perform(asCustomer(post("/internal/agent/actions/" + actionId + "/confirm"))).andExpect(status().isConflict());
    }
}
