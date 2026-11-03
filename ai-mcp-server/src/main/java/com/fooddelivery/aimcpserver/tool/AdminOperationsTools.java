package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
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

/** Platform administrators: deliveries and restaurants waiting for approval (delivery- and restaurant-service). */
@Component
public class AdminOperationsTools extends BaseTools {

    public AdminOperationsTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        super(gateway, callers, clock, mapper);
    }

    @Tool(name = "admin_delivery_summary", description = "Delivery report for a period: deliveries per status, delivered, "
            + "cancelled, the average delivery time in minutes (from food ready to delivered) and how many deliveries "
            + "are delayed right now. With no dates it means today.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN)
    public ToolResult adminDeliverySummary(
            @ToolParam(description = "First day, yyyy-MM-dd. Omit for today.", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd. Omit for today.", required = false) String toDate,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
        JsonNode report = gateway.get(caller, "/api/v1/admin/deliveries/summary",
                Map.of("fromDate", range.from().toString(), "toDate", range.to().toString()));

        ObjectNode summary = object();
        summary.put("from", range.from().toString());
        summary.put("to", range.to().toString());
        summary.put("total", report.path("total").asLong(0));
        summary.put("delivered", report.path("delivered").asLong(0));
        summary.put("cancelled", report.path("cancelled").asLong(0));
        JsonNode average = report.path("averageDeliveryMinutes");
        if (average.isNumber()) {
            summary.put("averageDeliveryMinutes", Math.round(average.asDouble() * 10) / 10.0);
        } else {
            summary.put("averageDeliveryMinutes", "none delivered in this period");
        }
        summary.put("delayedNow", report.path("delayedNow").asLong(0));
        if (report.path("byStatus").isObject()) {
            summary.set("byStatus", report.path("byStatus").deepCopy());
        }
        Card card = Cards.stats("Deliveries, " + range.from() + (range.from().equals(range.to()) ? "" : " to " + range.to()),
                List.of(Cards.stat("Deliveries", report.path("total").asLong(0), null),
                        Cards.stat("Delivered", report.path("delivered").asLong(0), null),
                        Cards.stat("Cancelled", report.path("cancelled").asLong(0), null),
                        Cards.stat("Average time", average.isNumber()
                                ? Math.round(average.asDouble()) + " min" : "n/a", "food ready to delivered"),
                        Cards.stat("Delayed now", report.path("delayedNow").asLong(0), "late right now")));
        return result(summary, card);
    }

    @Tool(name = "admin_delayed_deliveries", description = "Deliveries that are late right now (no partner 10 minutes "
            + "after the food was ready, or not delivered after 45 minutes), longest waiting first.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN)
    public ToolResult adminDelayedDeliveries(
            @ToolParam(description = "How many deliveries, 1 to 25 (default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        int size = ToolArgs.limit(limit, 10, Card.MAX_ITEMS);
        JsonNode page = gateway.get(caller, "/api/v1/admin/deliveries/delayed", Map.of("size", size));

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode delivery : page.path("content")) {
            long orderId = ToolJson.number(delivery, "orderId");
            ObjectNode row = object();
            row.put("deliveryId", ToolJson.number(delivery, "id"));
            row.put("orderId", orderId);
            row.put("status", ToolJson.text(delivery, "status"));
            row.put("restaurant", ToolJson.text(delivery, "restaurantName"));
            row.put("partner", ToolJson.text(delivery, "partnerName"));
            row.put("minutesSinceReady", delivery.path("minutesSinceReady").asLong(0));
            rows.add(row);
            items.add(Cards.item(orderId, "Order #" + orderId, ToolJson.text(delivery, "restaurantName"),
                    List.of(ToolJson.text(delivery, "status")),
                    List.of(Cards.meta("Partner", delivery.path("partnerName").isNull()
                                    || ToolJson.text(delivery, "partnerName").isEmpty()
                                    ? "not assigned" : ToolJson.text(delivery, "partnerName")),
                            Cards.meta("Waiting", delivery.path("minutesSinceReady").asLong(0) + " min"),
                            Cards.meta("Drop-off", ToolJson.text(delivery, "dropoffCity"))),
                    null, RoutePaths.adminDelivery(ToolJson.number(delivery, "id"))));
        }
        ObjectNode summary = object();
        summary.put("totalDelayed", page.path("totalElements").asLong(rows.size()));
        ToolJson.putCapped(summary, "deliveries", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("ORDER", "Delayed deliveries", items));
    }

    @Tool(name = "admin_pending_restaurants", description = "Restaurants waiting for the administrator's approval "
            + "(status PENDING), oldest submission first.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN)
    public ToolResult adminPendingRestaurants(
            @ToolParam(description = "How many restaurants, 1 to 25 (default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        int size = ToolArgs.limit(limit, 10, Card.MAX_ITEMS);
        JsonNode page = gateway.get(caller, "/api/v1/admin/restaurants",
                Map.of("status", "PENDING", "sort", "createdAt,asc", "size", size));

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode restaurant : page.path("content")) {
            long id = ToolJson.number(restaurant, "id");
            ObjectNode row = object();
            row.put("id", id);
            row.put("name", ToolJson.text(restaurant, "name"));
            row.put("city", ToolJson.text(restaurant, "city"));
            row.put("submittedAt", ToolJson.text(restaurant, "createdAt"));
            rows.add(row);
            List<String> cuisines = new ArrayList<>();
            restaurant.path("cuisines").forEach(c -> cuisines.add(c.asString("")));
            items.add(Cards.item(id, ToolJson.text(restaurant, "name"), ToolJson.text(restaurant, "city"), cuisines,
                    List.of(Cards.meta("Submitted", ToolJson.text(restaurant, "createdAt"))),
                    null, RoutePaths.adminRestaurant(id)));
        }
        ObjectNode summary = object();
        summary.put("totalPending", page.path("totalElements").asLong(rows.size()));
        ToolJson.putCapped(summary, "restaurants", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("RESTAURANT", "Restaurants waiting for approval", items));
    }
}
