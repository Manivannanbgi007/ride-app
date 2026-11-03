package com.fooddelivery.aimcpclient.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import com.fooddelivery.aimcpclient.MutableClock;
import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatRequest;
import com.fooddelivery.aimcpclient.dto.agent.AgentChatResponse;
import com.fooddelivery.aimcpclient.dto.response.ActionConfirmResponse;
import com.fooddelivery.aimcpclient.dto.response.StatusResponse;
import com.fooddelivery.aimcpclient.exception.AgentCallException;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import com.fooddelivery.aimcpclient.identity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * MOCKED ai-mcp-server (MockRestServiceServer): what is sent, how failures become user-safe messages, and the
 * circuit breaker. No test here talks to a real network.
 */
class AgentServerClientMockedTest {

    private static final String BASE = "http://agent.internal:8098";
    private static final CallerIdentity CALLER = new CallerIdentity(7, Role.CUSTOMER, "token-of-7");
    private static final AgentChatRequest CHAT = new AgentChatRequest("hello", List.of());

    private final MutableClock clock = new MutableClock();
    private MockRestServiceServer server;
    private AgentServerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("X-Service-Token", "secret-token");
        server = MockRestServiceServer.bindTo(builder).build();
        AppProperties properties = new AppProperties(
                new AppProperties.AgentServer(BASE, "secret-token", Duration.ofSeconds(2), Duration.ofSeconds(150),
                        262_144, 5, Duration.ofSeconds(20)), null, null, null);
        client = new AgentServerClient(builder.build(), JsonMapper.builder().build(), clock, properties);
    }

    @Test
    void chatSendsTheServiceTokenAndTheCallersAccessTokenAndKeepsCardsOpaque() {
        server.expect(requestTo(BASE + "/internal/agent/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "secret-token"))
                .andExpect(header("Authorization", "Bearer token-of-7"))
                .andExpect(content().json("{\"message\":\"hello\",\"history\":[]}"))
                .andRespond(withSuccess("""
                        {"text":"Hi","cards":[{"type":"anything","x":[1,2]}],"links":[{"label":"Orders","path":"/orders"}],
                         "pendingAction":null,"toolsUsed":["my_orders"],"model":"qwen","durationMs":1200,"toolCalls":1,
                         "somethingNew":true}""", MediaType.APPLICATION_JSON));

        AgentChatResponse reply = client.chat(CALLER, CHAT);

        assertThat(reply.text()).isEqualTo("Hi");
        assertThat(reply.cards().get(0).get("type").asString()).isEqualTo("anything");
        assertThat(reply.toolsUsed()).containsExactly("my_orders");
        server.verify();
    }

    @Test
    void confirmAndCancelAreForwardedWithTheCallersAccessToken() {
        server.expect(requestTo(BASE + "/internal/agent/actions/act-1/confirm"))
                .andExpect(header("Authorization", "Bearer token-of-7"))
                .andRespond(withSuccess("""
                        {"status":"CREATED","order":{"id":"55","status":"PLACED","total":250.50,"currency":"INR"},
                         "message":"Order placed","link":{"label":"View order","path":"/orders/55"}}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/agent/actions/act-1/cancel"))
                .andExpect(header("Authorization", "Bearer token-of-7"))
                .andRespond(withNoContent());

        ActionConfirmResponse confirmed = client.confirm(CALLER, "act-1");
        client.cancel(CALLER, "act-1");

        assertThat(confirmed.status()).isEqualTo("CREATED");
        assertThat(confirmed.order().id()).isEqualTo("55");
        assertThat(confirmed.link().path()).isEqualTo("/orders/55");
        server.verify();
    }

    @Test
    void statusNeedsNoIdentity() {
        server.expect(requestTo(BASE + "/internal/agent/status"))
                .andExpect(header("X-Service-Token", "secret-token"))
                .andRespond(withSuccess("{\"available\":true,\"model\":\"qwen\",\"message\":null}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.status()).isEqualTo(new StatusResponse(true, "qwen", null));
    }

    @Test
    void actionErrorsKeepTheirStatusWithAUserSafeMessage() {
        for (HttpStatus status : List.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.GONE)) {
            server.reset();
            server.expect(requestTo(BASE + "/internal/agent/actions/a/confirm"))
                    .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"title\":\"x\",\"detail\":\"jdbc:mysql://secret-host:3306 failed\"}"));

            assertThatThrownBy(() -> client.confirm(CALLER, "a"))
                    .isInstanceOfSatisfying(AgentCallException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(status);
                        assertThat(e.getMessage()).doesNotContain("secret-host", "jdbc", "agent.internal");
                    });
        }
    }

    @Test
    void aRefusedChangeKeepsTheServersPlainReasonButNothingTechnical() {
        server.expect(requestTo(BASE + "/internal/agent/actions/a/confirm"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"title\":\"Change refused\",\"detail\":"
                                + "\"Delivery 31 is PICKED_UP and cannot be picked up now\"}"));
        assertThatThrownBy(() -> client.confirm(CALLER, "a"))
                .isInstanceOfSatisfying(AgentCallException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Delivery 31 is PICKED_UP and cannot be picked up now");
                });

        server.reset();
        server.expect(requestTo(BASE + "/internal/agent/actions/a/confirm"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Cash on delivery: collect exactly INR 562.90\"}"));
        assertThatThrownBy(() -> client.confirm(CALLER, "a"))
                .isInstanceOfSatisfying(AgentCallException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getMessage()).isEqualTo("Cash on delivery: collect exactly INR 562.90");
                });

        server.reset();
        server.expect(requestTo(BASE + "/internal/agent/actions/a/confirm"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":"
                                + "\"org.hibernate.exception.ConstraintViolationException at com.x.Y.save(Y.java:42)\"}"));
        assertThatThrownBy(() -> client.confirm(CALLER, "a"))
                .isInstanceOfSatisfying(AgentCallException.class, e ->
                        assertThat(e.getMessage()).isEqualTo("The platform refused this change. Please ask again."));
    }

    @Test
    void aServerFailureBecomes503WithoutLeakingAnything() {
        server.expect(requestTo(BASE + "/internal/agent/chat"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"detail\":\"NullPointerException at com.x.Secret:42 on host agent.internal\"}"));

        assertThatThrownBy(() -> client.chat(CALLER, CHAT))
                .isInstanceOfSatisfying(AgentCallException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo(AgentServerClient.MSG_UNAVAILABLE);
                });
    }

    @Test
    void aSlowAnswerBecomes504WithTheSlowMessage() {
        server.expect(requestTo(BASE + "/internal/agent/chat"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

        assertThatThrownBy(() -> client.chat(CALLER, CHAT))
                .isInstanceOfSatisfying(AgentCallException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(e.getMessage()).isEqualTo("The assistant took too long to answer. Please try again.");
                });
    }

    @Test
    void aServerThatCannotBeReachedBecomes503() {
        server.expect(requestTo(BASE + "/internal/agent/chat")).andRespond(withException(new ConnectException("refused")));
        server.expect(requestTo(BASE + "/internal/agent/chat"))
                .andRespond(withException(new HttpConnectTimeoutException("connect timed out")));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.chat(CALLER, CHAT))
                    .isInstanceOfSatisfying(AgentCallException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    @Test
    void anOversizedAnswerIsRejected() {
        String huge = "{\"text\":\"" + "x".repeat(262_200) + "\"}";
        server.expect(requestTo(BASE + "/internal/agent/chat")).andRespond(withSuccess(huge, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(CALLER, CHAT))
                .isInstanceOfSatisfying(AgentCallException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void anAnswerThatIsNotJsonIsRejected() {
        server.expect(requestTo(BASE + "/internal/agent/chat")).andRespond(withSuccess("<html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(CALLER, CHAT))
                .isInstanceOfSatisfying(AgentCallException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void afterFiveFailuresInARowTheServerIsNotCalledFor20Seconds() {
        // exactly 5 calls reach the server (and each is made only once: no retries)
        server.expect(ExpectedCount.times(5), requestTo(BASE + "/internal/agent/chat"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.chat(CALLER, CHAT)).isInstanceOf(AgentCallException.class);
        }

        // 6th: answered at once by the open breaker (the mock would fail the test if it were called)
        assertThatThrownBy(() -> client.chat(CALLER, CHAT))
                .isInstanceOfSatisfying(AgentCallException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo(AgentServerClient.MSG_UNAVAILABLE);
                });
        server.verify();

        // after the open period a call goes through again, and a success closes the breaker
        clock.advance(Duration.ofSeconds(21));
        server.reset();
        server.expect(ExpectedCount.times(2), requestTo(BASE + "/internal/agent/chat"))
                .andRespond(withSuccess("{\"text\":\"back\"}", MediaType.APPLICATION_JSON));
        assertThat(client.chat(CALLER, CHAT).text()).isEqualTo("back");
        assertThat(client.chat(CALLER, CHAT).text()).isEqualTo("back");
    }

    @Test
    void clientErrorsDoNotOpenTheBreaker() {
        server.expect(ExpectedCount.times(8), requestTo(BASE + "/internal/agent/actions/a/confirm"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        for (int i = 0; i < 8; i++) {
            assertThatThrownBy(() -> client.confirm(CALLER, "a"))
                    .isInstanceOfSatisfying(AgentCallException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        server.verify();
    }
}
