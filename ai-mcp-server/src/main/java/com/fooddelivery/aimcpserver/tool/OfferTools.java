package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Coupons usable today (promotion-service). Public data; offered to owners and customers. */
@Component
public class OfferTools extends BaseTools {

    public OfferTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        super(gateway, callers, clock, mapper);
    }

    @Tool(name = "current_offers", description = "Coupons and offers that can be used today: code, what it gives, minimum "
            + "order amount, valid until and the restaurant it is limited to (none means every restaurant). "
            + "With restaurantId, that restaurant's own coupons are included too.")
    @ToolPolicy(roles = {Role.RESTAURANT_ADMIN, Role.CUSTOMER})
    public ToolResult currentOffers(
            @ToolParam(description = "Also include the coupons of this restaurant id", required = false) Long restaurantId,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN, Role.CUSTOMER);
        Map<String, Object> query = new LinkedHashMap<>();
        if (restaurantId != null) {
            long id = ToolArgs.positiveId("restaurantId", restaurantId);
            if (caller.role() == Role.RESTAURANT_ADMIN) {
                requireOwnedRestaurant(caller, id);
            }
            query.put("restaurantId", id);
        }
        JsonNode offers = gateway.get(caller, "/api/v1/coupons", query);

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode offer : offers) {
            String code = ToolJson.text(offer, "code");
            boolean restaurantOnly = offer.path("restaurantId").isNumber();
            ObjectNode row = object();
            row.put("code", code);
            row.put("description", ToolJson.text(offer, "description"));
            row.put("discount", ToolJson.text(offer, "summary"));
            row.put("minOrderAmount", ToolJson.amountText(offer, "minOrderAmount"));
            row.put("validUntil", ToolJson.text(offer, "validUntil"));
            if (restaurantOnly) {
                row.put("onlyAtRestaurantId", offer.path("restaurantId").asLong(0));
            }
            rows.add(row);
            items.add(Cards.item(code, code, ToolJson.text(offer, "summary"),
                    List.of(restaurantOnly ? "One restaurant" : "All restaurants"),
                    List.of(Cards.meta("Details", ToolJson.text(offer, "description")),
                            Cards.meta("Min order", ToolJson.amountText(offer, "minOrderAmount")),
                            Cards.meta("Valid until", ToolJson.text(offer, "validUntil"))),
                    null, caller.role() == Role.CUSTOMER ? RoutePaths.offers() : null));
        }
        ObjectNode summary = object();
        summary.put("offerCount", rows.size());
        ToolJson.putCapped(summary, "offers", rows, LIST_BUDGET);
        if (rows.isEmpty()) {
            summary.put("note", "There are no offers valid today.");
        }
        return result(summary, items.isEmpty() ? null : Cards.entities("OFFER", "Offers", items));
    }
}
