package com.fooddelivery.aimcpserver.controller;

import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.agent.AgentService;
import com.fooddelivery.aimcpserver.client.OllamaStatusClient;
import com.fooddelivery.aimcpserver.dto.ChatRequest;
import com.fooddelivery.aimcpserver.dto.ChatResponse;
import com.fooddelivery.aimcpserver.dto.ConfirmResponse;
import com.fooddelivery.aimcpserver.dto.StatusResponse;
import com.fooddelivery.aimcpserver.exception.ApiException;
import com.fooddelivery.aimcpserver.proposal.ProposalService;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.CallerTokenVerifier;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.web.RequestIdFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server API used by ai-mcp-client (not routed by the gateway; the service-token filter guards it).
 * Who is asking comes from the caller's access token (Authorization: Bearer), checked again here.
 * <p>
 * Confirm and cancel serve both kinds of prepared change: an order proposal (a plain UUID, customers only) and any
 * other action ("act_" + UUID, any role: the action service checks it belongs to this user and role).
 */
@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final ProposalService proposalService;
    private final ActionService actionService;
    private final OllamaStatusClient statusClient;
    private final CallerTokenVerifier tokenVerifier;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                             @Valid @RequestBody ChatRequest request) {
        return agentService.chat(caller(authorization), request);
    }

    @PostMapping("/actions/{actionId}/confirm")
    public ConfirmResponse confirm(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                   @PathVariable String actionId) {
        if (ActionService.isActionId(actionId)) {
            return actionService.confirm(caller(authorization), actionId);
        }
        return proposalService.confirm(customer(authorization), actionId);
    }

    @PostMapping("/actions/{actionId}/cancel")
    public ResponseEntity<Void> cancel(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                       @PathVariable String actionId) {
        if (ActionService.isActionId(actionId)) {
            actionService.cancel(caller(authorization), actionId);
        } else {
            proposalService.cancel(customer(authorization), actionId);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return statusClient.check();
    }

    private CallerContext caller(String authorization) {
        return tokenVerifier.verify(authorization, MDC.get(RequestIdFilter.MDC_REQUEST_ID));
    }

    // Only customers have order proposals
    private CallerContext customer(String authorization) {
        CallerContext caller = caller(authorization);
        if (caller.role() != Role.CUSTOMER) {
            throw ApiException.forbidden("Only customers can confirm or cancel an order proposal.");
        }
        return caller;
    }
}
