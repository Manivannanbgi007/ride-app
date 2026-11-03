package com.fooddelivery.aimcpserver.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.client.GatewayException;
import com.fooddelivery.aimcpserver.draft.OrderDraftService;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.support.ToolText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Facts the server knows about the caller, put into the system prompt so the model does not have to remember or guess
 * them (a small model forgets earlier turns and invents ids): an owner's restaurants, a customer's order in progress.
 * Everything here is read with the caller's own identity, so it can never show more than the caller may see.
 */
@Slf4j
@Component
public class PromptContext {

    private final GatewayClient gateway;
    private final OrderDraftService drafts;

    public PromptContext(GatewayClient gateway, OrderDraftService drafts) {
        this.gateway = gateway;
        this.drafts = drafts;
    }

    /** The extra block for this caller's system prompt, or an empty text. Never throws: it is only a help. */
    public String blockFor(CallerContext caller) {
        try {
            return switch (caller.role()) {
                case RESTAURANT_ADMIN -> ownerRestaurants(caller);
                case CUSTOMER -> drafts.promptBlock(caller.userId());
                default -> "";
            };
        } catch (GatewayException e) {
            log.debug("Prompt context not available: {}", e.getClass().getSimpleName());
            return "";
        }
    }

    private String ownerRestaurants(CallerContext caller) {
        JsonNode mine = gateway.get(caller, "/api/v1/restaurants/mine", Map.of());
        List<String> options = new ArrayList<>();
        for (JsonNode restaurant : mine) {
            options.add(ToolText.clean(restaurant.path("name").asString(""), 60) + " (id " + restaurant.path("id").asLong(0)
                    + ")");
        }
        if (options.isEmpty()) {
            return "\nThe owner has no restaurant yet.\n";
        }
        return "\nThe owner's restaurants (data from the platform): " + String.join("; ", options) + ". "
                + (options.size() == 1
                ? "There is only one: use it and leave restaurantId out of the tool calls."
                : "Use exactly these ids for reports; if the owner did not say which one, ask. Changes (accept, "
                + "reject, ready, sold out) need no restaurant: the prepare tools find it.") + "\n";
    }
}
