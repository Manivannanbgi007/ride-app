package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

/** Platform administrators: order numbers and order lists (order-service admin APIs). */
@Component
public class AdminOrderTools extends BaseTools {

    private static final String ORDERS = "/api/v1/admin/orders";

    /** What "top restaurants" can mean; the order-service ranks by revenue and by cancellations. */
    public enum RankBy {
        REVENUE, ORDERS, CANCELLATIONS
    }

    public AdminOrderTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        super(gateway, callers, clock, mapper);
    }

    @Tool(name = "admin_order_stats", description = "Platform-wide order numbers for a period: how many orders, delivered, "
            + "cancelled or rejected, still in progress, revenue (delivered orders), discounts and the average order value. "
            + "totalOrders is ALL orders created in the period, whatever their status: use it for 'how many orders were placed'. "
            + "Dates are calendar days; with no dates it means today.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN)
    public ToolResult adminOrderStats(
            @ToolParam(description = "First day, yyyy-MM-dd. Omit for today.", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd. Omit for today.", required = false) String toDate,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
        JsonNode stats = gateway.get(caller, ORDERS + "/stats",
                Map.of("fromDate", range.from().toString(), "toDate", range.to().toString()));

        String currency = ToolJson.text(stats, "currency");
        ObjectNode summary = object();
        summary.put("from", range.from().toString());
        summary.put("to", range.to().toString());
        summary.put("currency", currency);
        summary.put("orders", stats.path("orders").asLong(0));
        summary.put("delivered", stats.path("completed").asLong(0));
        summary.put("cancelledOrRejected", stats.path("cancelled").asLong(0));
        summary.put("inProgress", stats.path("inProgress").asLong(0));
        // The figures a model would add up itself are given ready-made
        summary.put("cancelledOnly", stats.path("byStatus").path("CANCELLED").asLong(0));
        summary.put("rejectedOnly", stats.path("byStatus").path("REJECTED").asLong(0));
        summary.put("revenueOfDeliveredOrders", ToolJson.amountText(stats, "revenue"));
        summary.put("discounts", ToolJson.amountText(stats, "discounts"));
        summary.put("averageOrderValue", ToolJson.amountText(stats, "averageOrderValue"));
        if (stats.path("byStatus").isObject()) {
            summary.set("byStatus", stats.path("byStatus").deepCopy());
        }
        String period = range.from().equals(range.to()) ? range.from().toString() : range.from() + " to " + range.to();
        Card card = Cards.stats("Orders, " + period, List.of(
                Cards.stat("Orders", stats.path("orders").asLong(0), null),
                Cards.stat("Delivered", stats.path("completed").asLong(0), null),
                Cards.stat("Cancelled or rejected", stats.path("cancelled").asLong(0), null),
                Cards.stat("In progress", stats.path("inProgress").asLong(0), null),
                Cards.stat("Revenue", ToolJson.amountText(stats, "revenue") + " " + currency, "delivered orders"),
                Cards.stat("Average order", ToolJson.amountText(stats, "averageOrderValue") + " " + currency, null)));
        return result(summary, card);
    }

    @Tool(name = "admin_list_orders", description = "Find or list orders on the platform (NOT for totals: use admin_order_stats "
            + "for how many orders were placed; the status PLACED only means waiting for the restaurant's decision). "
            + "Filters: status, period, restaurantId, "
            + "customerId. Newest first. With status CANCELLED or REJECTED the result has the cancellation reasons already "
            + "counted by the system (reasons, reasonsBasedOn: up to 20 orders are checked): report those counts as "
            + "the result's instruction says, never count or extrapolate yourself. Returns at most `limit` orders "
            + "(default 10) and the total number found.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN, timeoutSeconds = 15)
    public ToolResult adminListOrders(
            @ToolParam(description = "PAYMENT_PENDING, PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, "
                    + "DELIVERED, CANCELLED or REJECTED", required = false) String status,
            @ToolParam(description = "First day, yyyy-MM-dd", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd", required = false) String toDate,
            @ToolParam(description = "Only orders of this restaurant id", required = false) Long restaurantId,
            @ToolParam(description = "Only orders of this customer id", required = false) Long customerId,
            @ToolParam(description = "How many orders to return, 1 to 25 (default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        Map<String, Object> query = new LinkedHashMap<>();
        OrderStatus orderStatus = ToolArgs.optionalEnum("status", status, OrderStatus.class);
        if (orderStatus != null) {
            query.put("status", orderStatus.name());
        }
        if (fromDate != null && !fromDate.isBlank() || toDate != null && !toDate.isBlank()) {
            ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
            query.put("fromDate", range.from().toString());
            query.put("toDate", range.to().toString());
        }
        if (restaurantId != null) {
            query.put("restaurantId", ToolArgs.positiveId("restaurantId", restaurantId));
        }
        if (customerId != null) {
            query.put("customerId", ToolArgs.positiveId("customerId", customerId));
        }
        boolean cancelledList = orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.REJECTED;
        int size = ToolArgs.limit(limit, 10, Card.MAX_ITEMS);
        // A cancelled list is read wider than shown: the reasons of up to 20 orders are counted in code
        query.put("size", cancelledList ? Math.max(size, OrderViews.REASON_LOOKUPS) : size);
        query.put("sort", "createdAt,desc");

        JsonNode page = gateway.get(caller, ORDERS, query);
        ObjectNode summary = object();
        List<Card.Item> items = new ArrayList<>();
        OrderViews.addOrders(mapper, gateway, caller, ORDERS, page, size, cancelledList, summary, items,
                RoutePaths::adminOrder);
        return result(summary, items.isEmpty() ? null : Cards.entities("ORDER", "Orders", items));
    }

    @Tool(name = "admin_top_restaurants", description = "Restaurant performance for a period: the restaurants with the "
            + "most revenue, the most orders or the most cancellations (rankBy). With no dates it means today.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN)
    public ToolResult adminTopRestaurants(
            @ToolParam(description = "First day, yyyy-MM-dd. Omit for today.", required = false) String fromDate,
            @ToolParam(description = "Last day (inclusive), yyyy-MM-dd. Omit for today.", required = false) String toDate,
            @ToolParam(description = "REVENUE (default), ORDERS or CANCELLATIONS", required = false) String rankBy,
            @ToolParam(description = "How many restaurants, 1 to 25 (default 5)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        ToolArgs.DateRange range = ToolArgs.dateRange(fromDate, toDate, clock);
        RankBy ranking = ToolArgs.optionalEnum("rankBy", rankBy, RankBy.class);
        RankBy by = ranking == null ? RankBy.REVENUE : ranking;
        int size = ToolArgs.limit(limit, 5, Card.MAX_ITEMS);
        // The API lists the top N by revenue; to rank by orders we read more and sort here
        int fetch = by == RankBy.ORDERS ? 100 : size;
        JsonNode performance = gateway.get(caller, ORDERS + "/stats/restaurants", Map.of(
                "fromDate", range.from().toString(), "toDate", range.to().toString(), "limit", fetch));

        List<JsonNode> restaurants = new ArrayList<>();
        (by == RankBy.CANCELLATIONS ? performance.path("mostCancellations") : performance.path("topByRevenue"))
                .forEach(restaurants::add);
        if (by == RankBy.ORDERS) {
            restaurants.sort(Comparator.comparingLong((JsonNode r) -> r.path("orders").asLong(0)).reversed());
        }
        List<JsonNode> top = restaurants.size() > size ? restaurants.subList(0, size) : restaurants;

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode restaurant : top) {
            long id = ToolJson.number(restaurant, "restaurantId");
            ObjectNode row = object();
            row.put("restaurantId", id);
            row.put("name", ToolJson.text(restaurant, "restaurantName"));
            row.put("orders", restaurant.path("orders").asLong(0));
            row.put("delivered", restaurant.path("completed").asLong(0));
            row.put("cancelled", restaurant.path("cancelled").asLong(0));
            row.put("revenue", ToolJson.amountText(restaurant, "revenue"));
            row.put("cancellationRatePercent", ToolJson.amountText(restaurant, "cancellationRate"));
            rows.add(row);
            items.add(Cards.item(id, ToolJson.text(restaurant, "restaurantName"), null, List.of(),
                    List.of(Cards.meta("Orders", String.valueOf(restaurant.path("orders").asLong(0))),
                            Cards.meta("Delivered", String.valueOf(restaurant.path("completed").asLong(0))),
                            Cards.meta("Cancelled", String.valueOf(restaurant.path("cancelled").asLong(0))),
                            Cards.meta("Revenue", ToolJson.amountText(restaurant, "revenue")
                                    + " " + ToolJson.text(performance, "currency"))),
                    null, RoutePaths.adminRestaurant(id)));
        }
        ObjectNode summary = object();
        summary.put("from", range.from().toString());
        summary.put("to", range.to().toString());
        summary.put("rankedBy", by.name());
        summary.put("currency", ToolJson.text(performance, "currency"));
        summary.put("restaurantsWithOrders", performance.path("restaurantsWithOrders").asLong(0));
        ToolJson.putCapped(summary, "restaurants", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null
                : Cards.entities("RESTAURANT", "Top restaurants by " + by.name().toLowerCase(Locale.ROOT), items));
    }
}
