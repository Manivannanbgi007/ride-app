package com.fooddelivery.aimcpserver.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.aimcpserver.exception.ApiException;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.CallerTokenVerifier;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.ToolException;
import com.fooddelivery.aimcpserver.web.ServiceHeaders;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * The one place that says who is calling a tool. Two sources, both server-side: the agent puts the verified caller into
 * the ToolContext; for a call that arrives over MCP the access token of that HTTP request is checked (after the
 * service-token check). Tool arguments and the prompt are never consulted.
 */
@Component
public class CallerResolver {

    /** The MCP transport context of the current request carries these (see McpServerConfig). */
    public static final List<String> MCP_HEADERS = List.of(HttpHeaders.AUTHORIZATION, ServiceHeaders.REQUEST_ID);

    private final CallerTokenVerifier tokenVerifier;

    public CallerResolver(CallerTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    public Optional<CallerContext> find(ToolContext toolContext) {
        if (toolContext == null) {
            return Optional.empty();
        }
        Map<String, Object> context = toolContext.getContext();
        if (context.get(CallerContext.TOOL_CONTEXT_KEY) instanceof CallerContext caller) {
            return Optional.of(caller);
        }
        if (context.get("exchange") instanceof McpSyncServerExchange exchange) {
            return fromTransport(exchange.transportContext());
        }
        return Optional.empty();
    }

    /** The caller, which must have one of the roles; otherwise a ToolException the model can explain. */
    public CallerContext require(ToolContext toolContext, Role... allowed) {
        CallerContext caller = find(toolContext).orElseThrow(() -> new ToolException(ToolException.Kind.FORBIDDEN,
                "Authentication required: the caller's identity is missing."));
        caller.requireRole(allowed);
        return caller;
    }

    private Optional<CallerContext> fromTransport(McpTransportContext transport) {
        Object authorization = transport.get(HttpHeaders.AUTHORIZATION);
        Object requestId = transport.get(ServiceHeaders.REQUEST_ID);
        if (authorization == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(tokenVerifier.verify(authorization.toString(),
                    requestId == null ? null : requestId.toString()));
        } catch (ApiException e) {
            return Optional.empty();
        }
    }
}
