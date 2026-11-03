package com.fooddelivery.aimcpserver.config;

import java.util.HashMap;
import java.util.Map;

import com.fooddelivery.aimcpserver.tool.CallerResolver;
import com.fooddelivery.aimcpserver.tool.ToolCatalog;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP endpoint (Streamable HTTP, /mcp) exposes the same guarded tools as the agent. The service-token filter
 * protects the path; the access token (and request id) of each request is copied into the transport context, where
 * CallerResolver checks it. A call without a valid token gets an authentication error from the tool, never data.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            JsonMapper jsonMapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(request -> {
                    Map<String, Object> headers = new HashMap<>();
                    for (String name : CallerResolver.MCP_HEADERS) {
                        request.headers().header(name).stream().findFirst().ifPresent(value -> headers.put(name, value));
                    }
                    return McpTransportContext.create(headers);
                })
                .build();
    }

    /** All tools, each wrapped by the guard (role check, timeout, size cap, metrics). */
    @Bean
    public ToolCallbackProvider assistantToolCallbacks(ToolCatalog catalog) {
        return ToolCallbackProvider.from(catalog.all());
    }
}
