package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** What every tool class needs: the gateway, the caller resolver, the clock and a few helpers. */
public abstract class BaseTools implements AssistantTools {

    /** Room for list items inside the 6 KB tool result cap (the rest is the fixed part of the summary). */
    protected static final int LIST_BUDGET = 5000;

    protected final GatewayClient gateway;
    protected final CallerResolver callers;
    protected final Clock clock;
    protected final JsonMapper mapper;

    protected BaseTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        this.gateway = gateway;
        this.callers = callers;
        this.clock = clock;
        this.mapper = mapper;
    }

    protected ObjectNode object() {
        return mapper.createObjectNode();
    }

    protected static ToolResult result(ObjectNode summary, Card card) {
        return ToolResult.of(summary.toString(), card);
    }

    /** The ids of the restaurants the caller owns (the service decides; we only read its answer). */
    protected List<Long> ownedRestaurantIds(CallerContext caller) {
        JsonNode mine = gateway.get(caller, "/api/v1/restaurants/mine", Map.of());
        List<Long> ids = new ArrayList<>();
        for (JsonNode restaurant : mine) {
            ids.add(restaurant.path("id").asLong(0));
        }
        return ids;
    }

    /**
     * Refuses an id that is not one of the owner's restaurants before the business API is called. The service enforces
     * ownership as well (its own 403/404); this gives a clear message and saves a call.
     */
    protected void requireOwnedRestaurant(CallerContext caller, long restaurantId) {
        JsonNode mine = gateway.get(caller, "/api/v1/restaurants/mine", Map.of());
        List<String> options = new ArrayList<>();
        boolean owns = false;
        for (JsonNode restaurant : mine) {
            owns |= restaurant.path("id").asLong(0) == restaurantId;
            options.add(ToolJson.text(restaurant, "name") + " (id " + restaurant.path("id").asLong(0) + ")");
        }
        if (!owns) {
            throw new ToolException(ToolException.Kind.FORBIDDEN, "Restaurant " + restaurantId
                    + " is not one of your restaurants. Your restaurants: " + options + ". Call the tool again "
                    + (mine.size() == 1 ? "without restaurantId" : "with one of these ids") + ".");
        }
    }

    /**
     * The restaurant an owner tool works on. A given id must be one of the owner's restaurants (refused without calling
     * the business API otherwise). Without an id the owner's only restaurant is used; with several the owner is asked.
     */
    protected long resolveOwnerRestaurant(CallerContext caller, Long restaurantId) {
        if (restaurantId != null) {
            long id = ToolArgs.positiveId("restaurantId", restaurantId);
            requireOwnedRestaurant(caller, id);
            return id;
        }
        JsonNode mine = gateway.get(caller, "/api/v1/restaurants/mine", Map.of());
        if (mine.size() == 1) {
            return mine.get(0).path("id").asLong(0);
        }
        if (mine.isEmpty()) {
            throw ToolException.invalid("The owner has no restaurant yet.");
        }
        List<String> options = new ArrayList<>();
        for (JsonNode restaurant : mine) {
            options.add(ToolJson.text(restaurant, "name") + " (id " + restaurant.path("id").asLong(0) + ")");
        }
        throw new NeedInputException("NEED_INPUT: the owner has several restaurants. Ask which one they mean: " + options);
    }
}
