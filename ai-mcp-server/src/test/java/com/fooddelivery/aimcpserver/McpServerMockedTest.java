package com.fooddelivery.aimcpserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.testsupport.ContextTestSupport;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.TestJwts;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * MOCKED: a REAL MCP client (the MCP Java SDK) connects to the running server over Streamable HTTP, lists the tools and
 * calls one. The model is not involved: MCP hosts bring their own model; the tools and their guards are real.
 */
class McpServerMockedTest extends ContextTestSupport {

    @LocalServerPort
    private int port;

    @BeforeEach
    void reset() {
        GATEWAY.reset();
    }

    /** An MCP host: the service token, and the access token of the person it acts for (or none). */
    private McpSyncClient client(String token, String userId, String role) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) -> {
                    if (token != null) {
                        builder.header("X-Service-Token", token);
                    }
                    if (userId != null && role != null) {
                        builder.header("Authorization", TestJwts.bearer(Long.parseLong(userId), role));
                    }
                    builder.header("X-Request-Id", "mcp-test-1");
                })
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    @Test
    void mockedMcpClientListsAllToolsOverStreamableHttp() {
        try (McpSyncClient client = client(Fixtures.TOKEN, "2", "PLATFORM_ADMIN")) {
            client.initialize();

            List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();

            assertThat(names).contains("admin_order_stats", "owner_my_restaurants", "search_foods", "prepare_order",
                    "current_offers", "partner_current_delivery", "partner_prepare_delivery_step").hasSize(30);
            McpSchema.Tool stats = client.listTools().tools().stream()
                    .filter(tool -> tool.name().equals("admin_order_stats")).findFirst().orElseThrow();
            assertThat(stats.inputSchema().toString()).contains("fromDate").doesNotContain("toolContext");
        }
    }

    @Test
    void mockedMcpClientCallsAToolAsTheUserOfTheAccessToken() {
        GATEWAY.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);
        try (McpSyncClient client = client(Fixtures.TOKEN, "2", "PLATFORM_ADMIN")) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("admin_order_stats", Map.of("fromDate", "2026-10-09")));

            assertThat(result.isError()).isNotEqualTo(true);
            assertThat(text(result)).contains("\"orders\":12", "\"delivered\":7");
            assertThat(GATEWAY.calls()).hasSize(1);
            assertThat(GATEWAY.calls().get(0).header("Authorization")).startsWith("Bearer ");
            // Only the caller's own token says who the caller is
            assertThat(GATEWAY.calls().get(0).header("X-User-Id")).isNull();
            assertThat(GATEWAY.calls().get(0).header("X-User-Role")).isNull();
            assertThat(GATEWAY.calls().get(0).header("X-Request-Id")).isEqualTo("mcp-test-1");
        }
    }

    @Test
    void mockedMcpClientAToolOfAnotherRoleIsRefusedWithoutCallingTheGateway() {
        try (McpSyncClient client = client(Fixtures.TOKEN, "1", "CUSTOMER")) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("admin_order_stats", Map.of()));

            assertThat(text(result)).startsWith("ERROR").contains("not available for your role");
            assertThat(GATEWAY.calls()).isEmpty();
        }
    }

    @Test
    void mockedMcpClientWithoutAnAccessTokenTheToolReturnsAnAuthenticationError() {
        try (McpSyncClient client = client(Fixtures.TOKEN, null, null)) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("search_foods", Map.of()));

            assertThat(text(result)).startsWith("ERROR").contains("Authentication required");
            assertThat(GATEWAY.calls()).isEmpty();
        }
    }

    @Test
    void mockedMcpClientWithoutOrWithAWrongServiceTokenCannotEvenConnect() {
        assertThatThrownBy(() -> {
            try (McpSyncClient client = client(null, "2", "PLATFORM_ADMIN")) {
                client.initialize();
            }
        }).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> {
            try (McpSyncClient client = client("wrong", "2", "PLATFORM_ADMIN")) {
                client.initialize();
            }
        }).isInstanceOf(RuntimeException.class);
        assertThat(GATEWAY.calls()).isEmpty();
    }
}
