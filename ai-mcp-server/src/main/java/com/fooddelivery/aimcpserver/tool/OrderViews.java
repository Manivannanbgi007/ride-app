package com.fooddelivery.aimcpserver.tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.LongFunction;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.client.GatewayException;
import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.dto.Link;
import com.fooddelivery.aimcpserver.security.CallerContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** How an order (OrderSummaryResponse / OrderResponse of order-service) is shown to the model and on a card. */
final class OrderViews {

    /** Cancellation reasons are only in the full order: they are fetched for this many orders of a cancelled list. */
    static final int REASON_LOOKUPS = 20;

    private static final int PARALLEL_LOOKUPS = 4;
    private static final int LIST_BUDGET = 5000;
    private static final String NO_REASON = "(no reason given)";

    private OrderViews() {
    }

    static ObjectNode row(JsonMapper mapper, JsonNode order, String cancellationReason) {
        ObjectNode row = mapper.createObjectNode();
        row.put("id", ToolJson.number(order, "id"));
        row.put("restaurant", ToolJson.text(order, "restaurantName"));
        row.put("status", ToolJson.text(order, "status"));
        row.put("payment", ToolJson.text(order, "paymentMethod"));
        row.put("paymentStatus", ToolJson.text(order, "paymentStatus"));
        row.put("total", ToolJson.amountText(order, "totalAmount"));
        row.put("items", order.path("itemCount").asInt(0));
        row.put("placedAt", ToolJson.text(order, "createdAt"));
        if (cancellationReason != null && !cancellationReason.isBlank()) {
            row.put("cancellationReason", cancellationReason);
        }
        return row;
    }

    static Card.Item item(JsonNode order, String reason, Link link) {
        long id = ToolJson.number(order, "id");
        List<Card.Meta> meta = new ArrayList<>();
        meta.add(Cards.meta("Total", ToolJson.amountText(order, "totalAmount")));
        meta.add(Cards.meta("Payment", ToolJson.text(order, "paymentMethod")));
        meta.add(Cards.meta("Placed", ToolJson.text(order, "createdAt")));
        meta.add(Cards.meta("Reason", reason));
        return Cards.item(id, "Order #" + id, ToolJson.text(order, "restaurantName"),
                List.of(ToolJson.text(order, "status")), meta, null, link);
    }

    /**
     * Puts an order list into the summary and the card items. For a list of cancelled or rejected orders the reasons are
     * counted here, in code (the model never counts): the full order is read for up to 20 orders, four at a time, and
     * the summary gets "reasons" [{reason, count}] (most frequent first) and "reasonsBasedOn" with the exact numbers.
     */
    static void addOrders(JsonMapper mapper, GatewayClient gateway, CallerContext caller, String basePath, JsonNode page,
                          int showLimit, boolean cancelledList, ObjectNode summary, List<Card.Item> items,
                          LongFunction<Link> linker) {
        List<JsonNode> orders = new ArrayList<>();
        page.path("content").forEach(orders::add);
        Map<Long, String> reasons = cancelledList ? fetchReasons(gateway, caller, basePath, orders) : Map.of();
        long total = page.path("totalElements").asLong(orders.size());

        List<ObjectNode> rows = new ArrayList<>();
        for (JsonNode order : orders.subList(0, Math.min(showLimit, orders.size()))) {
            long id = ToolJson.number(order, "id");
            rows.add(row(mapper, order, reasons.get(id)));
            items.add(item(order, reasons.get(id), linker.apply(id)));
        }
        summary.put("totalFound", total);
        if (cancelledList) {
            summary.set("reasons", countReasons(mapper, reasons));
            boolean partial = reasons.size() < total;
            summary.put("reasonsBasedOn", partial ? reasons.size() + " of " + total + " cancelled or rejected orders"
                    : "all " + total + " cancelled or rejected orders");
            summary.put("instruction", partial
                    ? "Say 'among the " + reasons.size() + " orders I checked' when you report reasons and never "
                    + "extrapolate to the other orders."
                    : "These are the reasons of ALL the orders in this list: report the counts as they are.");
        }
        ToolJson.putCapped(summary, "orders", rows, LIST_BUDGET);
    }

    private static ArrayNode countReasons(JsonMapper mapper, Map<Long, String> reasons) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> shown = new LinkedHashMap<>();
        for (String reason : reasons.values()) {
            String text = reason == null || reason.isBlank() ? NO_REASON : reason;
            String key = text.toLowerCase(Locale.ROOT);
            counts.merge(key, 1, Integer::sum);
            shown.putIfAbsent(key, text);
        }
        ArrayNode array = mapper.createArrayNode();
        counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> array.add(mapper.createObjectNode().put("reason", shown.get(entry.getKey()))
                        .put("count", entry.getValue())));
        return array;
    }

    // A failed lookup is skipped (and not counted in "reasonsBasedOn")
    private static Map<Long, String> fetchReasons(GatewayClient gateway, CallerContext caller, String basePath,
                                                  List<JsonNode> orders) {
        Semaphore permits = new Semaphore(PARALLEL_LOOKUPS);
        Map<Long, Future<String>> pending = new LinkedHashMap<>();
        Map<Long, String> reasons = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (JsonNode order : orders.subList(0, Math.min(REASON_LOOKUPS, orders.size()))) {
                long id = ToolJson.number(order, "id");
                pending.put(id, executor.submit(() -> {
                    permits.acquire();
                    try {
                        return ToolJson.text(gateway.get(caller, basePath + "/" + id, Map.of()), "cancellationReason");
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Map.Entry<Long, Future<String>> entry : pending.entrySet()) {
                try {
                    reasons.put(entry.getKey(), entry.getValue().get());
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof GatewayException)) {
                        throw new IllegalStateException("Reason lookup failed", e.getCause());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return reasons;
    }
}
