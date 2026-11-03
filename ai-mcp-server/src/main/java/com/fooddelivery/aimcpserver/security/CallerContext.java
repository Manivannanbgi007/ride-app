package com.fooddelivery.aimcpserver.security;

import java.util.Arrays;

import com.fooddelivery.aimcpserver.support.ToolException;

/**
 * Who is asking, from the caller's verified access token (CallerTokenVerifier). It reaches the tools through the Spring
 * AI ToolContext, never through tool arguments or the prompt, so neither the model nor the user's text can change it.
 *
 * @param accessToken the caller's token, sent on to the platform with every call made for them (GatewayClient); only
 *                    the security and client packages may read it (ArchitectureTest). Proposals and actions kept in
 *                    Redis store the user id and role only, never the token.
 */
public record CallerContext(long userId, Role role, String requestId, String accessToken) {

    /** Key under which the agent (and the MCP resolver) puts the caller into the ToolContext. */
    public static final String TOOL_CONTEXT_KEY = "caller";

    // Records print every field: the token must not reach a log line by accident
    @Override
    public String toString() {
        return "CallerContext[userId=" + userId + ", role=" + role + ", requestId=" + requestId + ", accessToken=****]";
    }

    /** Role re-check inside the tool, in addition to the role-specific tool set the model was given. */
    public void requireRole(Role... allowed) {
        if (!Arrays.asList(allowed).contains(role)) {
            throw new ToolException(ToolException.Kind.FORBIDDEN, "This tool is not available for your role.");
        }
    }
}
