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

/**
 * Restaurant owners. Every tool that takes a restaurantId first checks it is one of the owner's restaurants (without
 * calling the business API otherwise); the business services check ownership again on their side.
 */
@Component
public class OwnerTools extends BaseTools {

    private static final String MINE = "/api/v1/restaurants/mine";

    public OwnerTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        super(gateway, callers, clock, mapper);
    }

    @Tool(name = "owner_my_restaurants", description = "The restaurants of the logged-in owner: id, name, city, status. "
            + "Use it first when you need a restaurantId.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN)
    public ToolResult ownerMyRestaurants(ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        JsonNode mine = gateway.get(caller, MINE, Map.of());
        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode restaurant : mine) {
            long id = ToolJson.number(restaurant, "id");
            ObjectNode row = object();
            row.put("id", id);
            row.put("name", ToolJson.text(restaurant, "name"));
            row.put("city", ToolJson.text(restaurant, "city"));
            row.put("status", ToolJson.text(restaurant, "status"));
            rows.add(row);
            List<String> cuisines = new ArrayList<>();
            restaurant.path("cuisines").forEach(c -> cuisines.add(c.asString("")));
            List<String> badges = new ArrayList<>(List.of(ToolJson.text(restaurant, "status")));
            badges.addAll(cuisines);
            // Only an ACTIVE restaurant has a public page
            items.add(Cards.item(id, ToolJson.text(restaurant, "name"), ToolJson.text(restaurant, "city"), badges,
                    List.of(), null, "ACTIVE".equals(restaurant.path("status").asString(""))
                            ? RoutePaths.restaurantPage(id) : null));
        }
        ObjectNode summary = object();
        ToolJson.putCapped(summary, "restaurants", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("RESTAURANT", "Your restaurants", items));
    }

    @Tool(name = "owner_sales_summary", description = "Sales of one of the owner's restaurants for a period: orders by "
            + "status, value of accepted orders and of delivered orders. With no dates it means today.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN)
    public ToolResult ownerSalesSummary(
            @ToolParam(description = "The restaurant id. Omit it when the owner has only one restaurant", required = false)
            Long restaurantId,
            @ToolParam(description = "First day, yyyy-MM-dd. Omit for today.", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd. Omit for today.", required = false) String toDate,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
        long id = resolveOwnerRestaurant(caller, restaurantId);
        JsonNode sales = gateway.get(caller, MINE + "/" + id + "/orders/sales-summary",
                Map.of("fromDate", range.from().toString(), "toDate", range.to().toString()));

        String currency = ToolJson.text(sales, "currency");
        ObjectNode summary = object();
        summary.put("restaurantId", id);
        summary.put("from", range.from().toString());
        summary.put("to", range.to().toString());
        summary.put("currency", currency);
        summary.put("totalOrders", sales.path("totalOrders").asLong(0));
        summary.put("acceptedOrdersValue", ToolJson.amountText(sales, "acceptedOrdersValue"));
        summary.put("deliveredOrdersValue", ToolJson.amountText(sales, "deliveredOrdersValue"));
        JsonNode byStatus = sales.path("ordersByStatus");
        // The figures a model would add up itself are given ready-made
        summary.put("delivered", byStatus.path("DELIVERED").asLong(0));
        summary.put("waitingForDecision", byStatus.path("PLACED").asLong(0));
        summary.put("cancelledOrRejected", byStatus.path("CANCELLED").asLong(0) + byStatus.path("REJECTED").asLong(0));
        summary.put("inProgress", byStatus.path("ACCEPTED").asLong(0) + byStatus.path("PREPARING").asLong(0)
                + byStatus.path("READY_FOR_PICKUP").asLong(0) + byStatus.path("OUT_FOR_DELIVERY").asLong(0));
        if (sales.path("ordersByStatus").isObject()) {
            summary.set("ordersByStatus", sales.path("ordersByStatus").deepCopy());
        }
        Card card = Cards.stats("Sales, " + range.from() + (range.from().equals(range.to()) ? "" : " to " + range.to()),
                List.of(Cards.stat("Orders", sales.path("totalOrders").asLong(0), null),
                        Cards.stat("Accepted orders", ToolJson.amountText(sales, "acceptedOrdersValue") + " " + currency,
                                "in progress or delivered"),
                        Cards.stat("Delivered orders", ToolJson.amountText(sales, "deliveredOrdersValue") + " " + currency,
                                null)));
        return result(summary, card);
    }

    @Tool(name = "owner_orders", description = "Orders of one of the owner's restaurants, newest first (oldest first for "
            + "PLACED, the orders waiting for a decision). Filter by status and period. With status CANCELLED or "
            + "REJECTED the result has the reasons already counted by the system (reasons, reasonsBasedOn): report them as "
            + "the result's instruction says and never count or extrapolate yourself.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN, timeoutSeconds = 15)
    public ToolResult ownerOrders(
            @ToolParam(description = "The restaurant id. Omit it when the owner has only one restaurant", required = false)
            Long restaurantId,
            @ToolParam(description = "PLACED (waiting for a decision), ACCEPTED, PREPARING, READY_FOR_PICKUP, "
                    + "OUT_FOR_DELIVERY, DELIVERED, CANCELLED or REJECTED", required = false) String status,
            @ToolParam(description = "First day, yyyy-MM-dd", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd", required = false) String toDate,
            @ToolParam(description = "How many orders, 1 to 25 (default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        OrderStatus orderStatus = ToolArgs.optionalEnum("status", status, OrderStatus.class);
        Map<String, Object> query = new LinkedHashMap<>();
        if (orderStatus != null) {
            query.put("status", orderStatus.name());
        }
        if (fromDate != null && !fromDate.isBlank() || toDate != null && !toDate.isBlank()) {
            ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
            query.put("fromDate", range.from().toString());
            query.put("toDate", range.to().toString());
        }
        int size = ToolArgs.limit(limit, 10, Card.MAX_ITEMS);
        boolean cancelledList = orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.REJECTED;
        // A cancelled list is read wider than shown: the reasons of up to 20 orders are counted in code
        query.put("size", cancelledList ? Math.max(size, OrderViews.REASON_LOOKUPS) : size);
        query.put("sort", orderStatus == OrderStatus.PLACED ? "createdAt,asc" : "createdAt,desc");
        long id = resolveOwnerRestaurant(caller, restaurantId);

        String base = MINE + "/" + id + "/orders";
        JsonNode page = gateway.get(caller, base, query);
        ObjectNode summary = object();
        summary.put("restaurantId", id);
        List<Card.Item> items = new ArrayList<>();
        // The owner has no order pages in the front-end yet, so no link
        OrderViews.addOrders(mapper, gateway, caller, base, page, size, cancelledList, summary, items, orderId -> null);
        return result(summary, items.isEmpty() ? null : Cards.entities("ORDER", "Orders", items));
    }

    @Tool(name = "owner_top_items", description = "The most ordered dishes of one of the owner's restaurants in a period "
            + "(quantity sold, number of orders, revenue). With no dates it means today.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN)
    public ToolResult ownerTopItems(
            @ToolParam(description = "The restaurant id. Omit it when the owner has only one restaurant", required = false)
            Long restaurantId,
            @ToolParam(description = "First day, yyyy-MM-dd. Omit for today.", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd. Omit for today.", required = false) String toDate,
            @ToolParam(description = "How many dishes, 1 to 25 (default 5)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
        int size = ToolArgs.limit(limit, 5, Card.MAX_ITEMS);
        long id = resolveOwnerRestaurant(caller, restaurantId);
        JsonNode top = gateway.get(caller, MINE + "/" + id + "/orders/top-items",
                Map.of("from", range.from().toString(), "to", range.to().toString(), "limit", size));

        List<ObjectNode> rows = new ArrayList<>();
        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (JsonNode item : top.path("items")) {
            ObjectNode row = object();
            row.put("menuItemId", ToolJson.number(item, "menuItemId"));
            row.put("name", ToolJson.text(item, "name"));
            row.put("quantity", item.path("quantity").asLong(0));
            row.put("orders", item.path("orders").asLong(0));
            row.put("revenue", ToolJson.amountText(item, "revenue"));
            rows.add(row);
            Map<String, Object> cells = new LinkedHashMap<>();
            cells.put("name", ToolJson.text(item, "name"));
            cells.put("quantity", item.path("quantity").asLong(0));
            cells.put("orders", item.path("orders").asLong(0));
            cells.put("revenue", ToolJson.amountText(item, "revenue"));
            tableRows.add(cells);
        }
        ObjectNode summary = object();
        summary.put("restaurantId", id);
        summary.put("from", range.from().toString());
        summary.put("to", range.to().toString());
        ToolJson.putCapped(summary, "items", rows, LIST_BUDGET);
        Card card = tableRows.isEmpty() ? null : Cards.table("Most ordered dishes",
                List.of(Cards.column("name", "Dish", Cards.LEFT), Cards.column("quantity", "Sold", Cards.RIGHT),
                        Cards.column("orders", "Orders", Cards.RIGHT), Cards.column("revenue", "Revenue", Cards.RIGHT)),
                tableRows);
        return result(summary, card);
    }

    @Tool(name = "owner_delivery_partners", description = "Which delivery partners delivered the most orders of one of the "
            + "owner's restaurants in a period, with the average delivery time in minutes. With no dates it means today.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN)
    public ToolResult ownerDeliveryPartners(
            @ToolParam(description = "The restaurant id. Omit it when the owner has only one restaurant", required = false)
            Long restaurantId,
            @ToolParam(description = "First day, yyyy-MM-dd. Omit for today.", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd. Omit for today.", required = false) String toDate,
            @ToolParam(description = "How many partners, 1 to 25 (default 5)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
        int size = ToolArgs.limit(limit, 5, Card.MAX_ITEMS);
        long id = resolveOwnerRestaurant(caller, restaurantId);
        JsonNode partners = gateway.get(caller, MINE + "/" + id + "/deliveries/partners",
                Map.of("from", range.from().toString(), "to", range.to().toString(), "limit", size));

        List<ObjectNode> rows = new ArrayList<>();
        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (JsonNode partner : partners.path("partners")) {
            ObjectNode row = object();
            row.put("partnerUserId", ToolJson.number(partner, "partnerUserId"));
            row.put("name", ToolJson.text(partner, "name"));
            row.put("delivered", partner.path("delivered").asLong(0));
            row.put("averageMinutes", ToolJson.amountText(partner, "averageMinutes"));
            rows.add(row);
            Map<String, Object> cells = new LinkedHashMap<>();
            cells.put("name", ToolJson.text(partner, "name"));
            cells.put("delivered", partner.path("delivered").asLong(0));
            cells.put("averageMinutes", ToolJson.amountText(partner, "averageMinutes"));
            tableRows.add(cells);
        }
        ObjectNode summary = object();
        summary.put("restaurantId", id);
        summary.put("from", range.from().toString());
        summary.put("to", range.to().toString());
        ToolJson.putCapped(summary, "partners", rows, LIST_BUDGET);
        Card card = tableRows.isEmpty() ? null : Cards.table("Delivery partners",
                List.of(Cards.column("name", "Partner", Cards.LEFT), Cards.column("delivered", "Delivered", Cards.RIGHT),
                        Cards.column("averageMinutes", "Avg minutes", Cards.RIGHT)), tableRows);
        return result(summary, card);
    }

    @Tool(name = "owner_menu", description = "The menu items of one of the owner's restaurants with price and whether each "
            + "is available. Use onlyUnavailable=true to list only the items that cannot be ordered now.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN)
    public ToolResult ownerMenu(
            @ToolParam(description = "The restaurant id. Omit it when the owner has only one restaurant", required = false)
            Long restaurantId,
            @ToolParam(description = "true to list only unavailable items", required = false) Boolean onlyUnavailable,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        long id = resolveOwnerRestaurant(caller, restaurantId);
        JsonNode menu = gateway.get(caller, MINE + "/" + id + "/menu-items", Map.of());

        boolean unavailableOnly = Boolean.TRUE.equals(onlyUnavailable);
        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode item : menu) {
            boolean available = item.path("available").asBoolean(false);
            if (unavailableOnly && available) {
                continue;
            }
            long itemId = ToolJson.number(item, "id");
            ObjectNode row = object();
            row.put("id", itemId);
            row.put("name", ToolJson.text(item, "name"));
            row.put("category", ToolJson.text(item, "categoryName"));
            row.put("price", ToolJson.amountText(item, "price"));
            row.put("type", ToolJson.text(item, "foodType"));
            row.put("available", available);
            row.put("status", ToolJson.text(item, "status"));
            rows.add(row);
            items.add(Cards.item(itemId, ToolJson.text(item, "name"), ToolJson.text(item, "categoryName"),
                    List.of(available ? "Available" : "Unavailable", ToolJson.text(item, "foodType")),
                    List.of(Cards.meta("Price", ToolJson.amountText(item, "price"))),
                    ToolJson.url(item, "imageUrl"), null));
        }
        ObjectNode summary = object();
        summary.put("restaurantId", id);
        summary.put("itemCount", rows.size());
        ToolJson.putCapped(summary, "items", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("FOOD", "Menu", items));
    }
}
