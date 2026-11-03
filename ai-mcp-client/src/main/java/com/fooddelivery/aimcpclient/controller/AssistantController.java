package com.fooddelivery.aimcpclient.controller;

import java.util.UUID;

import com.fooddelivery.aimcpclient.config.SuggestionCatalog;
import com.fooddelivery.aimcpclient.dto.request.ChatRequest;
import com.fooddelivery.aimcpclient.dto.response.ActionConfirmResponse;
import com.fooddelivery.aimcpclient.dto.response.ChatResponse;
import com.fooddelivery.aimcpclient.dto.response.ConversationResponse;
import com.fooddelivery.aimcpclient.dto.response.StatusResponse;
import com.fooddelivery.aimcpclient.dto.response.SuggestionsResponse;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import com.fooddelivery.aimcpclient.service.ActionService;
import com.fooddelivery.aimcpclient.service.ChatService;
import com.fooddelivery.aimcpclient.service.ConversationService;
import com.fooddelivery.aimcpclient.service.StatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only API the chatbot in the browser uses. The CallerIdentity parameter is always the first one: the caller
 * is identified (401/403/503) before the body is validated.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ActionService actionService;
    private final StatusService statusService;
    private final SuggestionCatalog suggestionCatalog;

    @PostMapping("/chat")
    public ChatResponse chat(CallerIdentity caller, @Valid @RequestBody ChatRequest request) {
        return chatService.chat(caller, request);
    }

    @GetMapping("/conversation")
    public ConversationResponse conversation(CallerIdentity caller,
                                             @RequestParam(required = false) UUID conversationId) {
        return conversationService.view(caller, conversationId);
    }

    @DeleteMapping("/conversation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void newChat(CallerIdentity caller, @RequestParam(required = false) UUID conversationId) {
        conversationService.delete(caller, conversationId);
    }

    @PostMapping("/actions/{actionId}/confirm")
    public ActionConfirmResponse confirm(CallerIdentity caller, @PathVariable String actionId) {
        return actionService.confirm(caller, actionId);
    }

    @PostMapping("/actions/{actionId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(CallerIdentity caller, @PathVariable String actionId) {
        actionService.cancel(caller, actionId);
    }

    @GetMapping("/suggestions")
    public SuggestionsResponse suggestions(CallerIdentity caller) {
        return new SuggestionsResponse(suggestionCatalog.forRole(caller.role()));
    }

    /** No identity needed and never an error status: the chat window just hides the assistant when it is down. */
    @GetMapping("/status")
    public StatusResponse status() {
        return statusService.status();
    }
}
