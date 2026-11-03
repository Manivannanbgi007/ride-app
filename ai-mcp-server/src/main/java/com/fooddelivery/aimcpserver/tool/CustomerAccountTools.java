package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.client.GatewayException;
import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Customers: their own saved addresses and orders. The services return only the caller's data (the user id is the
 * forwarded identity), so a customer can never read someone else's order through these tools.
 */
@Component
public class CustomerAccountTools extends BaseTools {

    private static final int RECENT_STEPS = 5;

    public CustomerAccountTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        super(gateway, callers, clock, mapper);
    }

    @Tool(name = "my_addresses", description = "The customer's saved delivery addresses (id, type, address, city, "
            + "whether it is the default). Use an address id for search_restaurants (near me) and prepare_order.")
    @ToolPolicy(roles = Role.CUSTOMER)
    public ToolResult myAddresses(ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        JsonNode addresses = gateway.get(caller, "/api/v1/users/me/addresses", Map.of());
        List<ObjectNode> rows = new ArrayList<>();
        for (JsonNode address : addresses) {
            ObjectNode row = object();
            row.put("id", ToolJson.number(address, "id"));
            row.put("type", ToolJson.text(address, "type"));
            row.put("line1", ToolJson.text(address, "line1"));
            row.put("line2", ToolJson.text(address, "line2"));
            row.put("city", ToolJson.text(address, "city"));
            row.put("postalCode", ToolJson.text(address, "postalCode"));
            row.put("isDefault", address.path("defaultAddress").asBoolean(false));
            // Coordinates stay out of the model's sight: the search tool reads them itself from the saved address
            row.put("hasMapLocation", address.path("latitude").isNumber() && address.path("longitude").isNumber());
            rows.add(row);
        }
        ObjectNode summary = object();
        summary.put("addressCount", rows.size());
        ToolJson.putCapped(summary, "addresses", rows, LIST_BUDGET);
        if (rows.isEmpty()) {
            summary.put("note", "The customer has no saved address. They can add one under Account > Addresses.");
        }
        return ToolResult.textOnly(summary.toString());
    }

    @Tool(name = "my_orders", description = "The customer's own orders, newest first, optionally only one status. "
            + "Returns at most `limit` orders (default 5) and the total number.")
    @ToolPolicy(roles = Role.CUSTOMER)
    public ToolResult myOrders(
            @ToolParam(description = "PAYMENT_PENDING, PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, "
                    + "DELIVERED, CANCELLED or REJECTED", required = false) String status,
            @ToolParam(description = "How many orders, 1 to 25 (default 5)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        Map<String, Object> query = new LinkedHashMap<>();
        OrderStatus orderStatus = ToolArgs.optionalEnum("status", status, OrderStatus.class);
        if (orderStatus != null) {
            query.put("status", orderStatus.name());
        }
        query.put("size", ToolArgs.limit(limit, 5, Card.MAX_ITEMS));
        query.put("sort", "createdAt,desc");
        JsonNode page = gateway.get(caller, "/api/v1/orders", query);

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode order : page.path("content")) {
            rows.add(OrderViews.row(mapper, order, null));
            items.add(OrderViews.item(order, null, RoutePaths.customerOrder(ToolJson.number(order, "id"))));
        }
        ObjectNode summary = object();
        summary.put("totalOrders", page.path("totalElements").asLong(rows.size()));
        ToolJson.putCapped(summary, "orders", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("ORDER", "Your orders", items));
    }

    @Tool(name = "my_order_status", description = "Status of one of the customer's own orders: items, total, status, "
            + "cancellation reason if cancelled, recent steps and, once the food is ready, the delivery status and "
            + "the delivery partner's name.")
    @ToolPolicy(roles = Role.CUSTOMER)
    public ToolResult myOrderStatus(
            @ToolParam(description = "The order id (from my_orders)") Long orderId,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        long id = ToolArgs.positiveId("orderId", orderId);
        JsonNode order = gateway.get(caller, "/api/v1/orders/" + id, Map.of());

        ObjectNode summary = OrderViews.row(mapper, order, ToolJson.text(order, "cancellationReason"));
        summary.put("restaurantId", ToolJson.number(order, "restaurantId"));
        ArrayNode lines = summary.putArray("lines");
        for (JsonNode line : order.path("items")) {
            lines.add(ToolJson.text(line, "name") + " x" + line.path("quantity").asInt(0));
        }
        JsonNode price = order.path("price");
        summary.put("total", ToolJson.amountText(price, "totalAmount"));
        summary.put("items", order.path("items").size());
        summary.put("currency", ToolJson.text(price, "currency"));
        summary.put("deliveryFee", ToolJson.amountText(price, "deliveryFee"));
        summary.put("discount", ToolJson.amountText(price, "discountAmount"));
        ArrayNode steps = summary.putArray("recentSteps");
        JsonNode timeline = order.path("timeline");
        for (int i = Math.max(0, timeline.size() - RECENT_STEPS); i < timeline.size(); i++) {
            steps.add(ToolJson.text(timeline.get(i), "status") + " at " + ToolJson.text(timeline.get(i), "at"));
        }
        summary.set("delivery", deliveryOf(caller, id));

        List<Card.Meta> meta = new ArrayList<>();
        meta.add(Cards.meta("Total", ToolJson.amountText(price, "totalAmount") + " " + ToolJson.text(price, "currency")));
        meta.add(Cards.meta("Payment", ToolJson.text(order, "paymentMethod") + ", " + ToolJson.text(order, "paymentStatus")));
        meta.add(Cards.meta("Delivery", summary.path("delivery").path("status").asString("")));
        meta.add(Cards.meta("Partner", summary.path("delivery").path("partner").asString("")));
        meta.add(Cards.meta("Reason", ToolJson.text(order, "cancellationReason")));
        Card card = Cards.entities("ORDER", "Order #" + id, List.of(Cards.item(id, "Order #" + id,
                ToolJson.text(order, "restaurantName"), List.of(ToolJson.text(order, "status")), meta, null,
                RoutePaths.customerOrder(id))));
        return result(summary, card);
    }

    // Tracking only exists once the restaurant has the food ready (404 before that): not an error
    private ObjectNode deliveryOf(CallerContext caller, long orderId) {
        ObjectNode delivery = object();
        try {
            JsonNode tracking = gateway.get(caller, "/api/v1/delivery/orders/" + orderId, Map.of());
            delivery.put("status", ToolJson.text(tracking, "status"));
            delivery.put("partner", ToolJson.text(tracking, "partnerName"));
        } catch (GatewayException e) {
            delivery.put("status", "not started yet");
        }
        return delivery;
    }
}
