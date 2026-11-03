package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.AddressText;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Delivery partners (riders). Every tool reads the partner's OWN data through the partner APIs
 * (/api/v1/delivery/deliveries/mine..., /api/v1/delivery/partners/me): delivery-service only ever returns the caller's
 * deliveries (another partner's delivery is a 404), so no id given by the model can reach someone else's data.
 * Read-only: accepting, picking up and delivering stay buttons in the partner app (or, later, confirmed actions).
 */
@Component
public class PartnerTools extends BaseTools {

    private static final String MINE = "/api/v1/delivery/deliveries/mine";
    private static final String NO_INSTRUCTIONS = "The platform records no delivery instructions for partners: "
            + "only the addresses, the customer's name and phone, and the cash to collect.";
    private static final int RECENT_STEPS = 5;
    /** How many recent deliveries are read to count today's: a partner's day never comes close */
    private static final int TODAY_LOOKUP = 100;
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public PartnerTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        super(gateway, callers, clock, mapper);
    }

    @Tool(name = "partner_current_delivery", description = "The delivery the logged-in delivery partner has right now: "
            + "an offer waiting for an answer (ASSIGNED), on the way to the restaurant (ACCEPTED) or on the way to the "
            + "customer (PICKED_UP), with the pickup and drop-off addresses, the customer's name and phone and the cash "
            + "to collect. Says so when there is none.")
    @ToolPolicy(roles = Role.DELIVERY_PARTNER)
    public ToolResult partnerCurrentDelivery(ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.DELIVERY_PARTNER);
        // 204 No Content when the partner has no delivery: the client turns it into an empty object
        JsonNode delivery = gateway.get(caller, MINE + "/current", Map.of());
        if (delivery.path("id").isMissingNode()) {
            ObjectNode summary = object();
            summary.put("currentDelivery", "none");
            summary.put("note", "No delivery right now. New offers arrive while the partner is online (AVAILABLE).");
            return result(summary, null);
        }
        return details(delivery);
    }

    @Tool(name = "partner_delivery_details", description = "One of the partner's own deliveries by its delivery id (from "
            + "partner_my_deliveries): status, restaurant and pickup address, customer and drop-off address, cash to "
            + "collect, times and recent steps.")
    @ToolPolicy(roles = Role.DELIVERY_PARTNER)
    public ToolResult partnerDeliveryDetails(
            @ToolParam(description = "The delivery id (not the order id)") Long deliveryId,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.DELIVERY_PARTNER);
        long id = ToolArgs.positiveId("deliveryId", deliveryId);
        return details(gateway.get(caller, MINE + "/" + id, Map.of()));
    }

    @Tool(name = "partner_my_deliveries", description = "The partner's own deliveries, newest first, optionally only one "
            + "status: ASSIGNED, ACCEPTED, PICKED_UP, DELIVERED or CANCELLED. Offers that were declined or not answered "
            + "are not in this list.")
    @ToolPolicy(roles = Role.DELIVERY_PARTNER)
    public ToolResult partnerMyDeliveries(
            @ToolParam(description = "Only this status", required = false) String status,
            @ToolParam(description = "How many deliveries, 1 to 25 (default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.DELIVERY_PARTNER);
        DeliveryStatus deliveryStatus = ToolArgs.optionalEnum("status", status, DeliveryStatus.class);
        int size = ToolArgs.limit(limit, 10, Card.MAX_ITEMS);
        Map<String, Object> query = new LinkedHashMap<>();
        if (deliveryStatus != null) {
            query.put("status", deliveryStatus.name());
        }
        query.put("size", size);
        query.put("sort", "createdAt,desc");
        JsonNode page = gateway.get(caller, MINE, query);

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode delivery : page.path("content")) {
            long id = ToolJson.number(delivery, "id");
            ObjectNode row = object();
            row.put("deliveryId", id);
            row.put("orderId", ToolJson.number(delivery, "orderId"));
            row.put("status", ToolJson.text(delivery, "status"));
            row.put("restaurant", ToolJson.text(delivery, "restaurantName"));
            row.put("from", ToolJson.text(delivery, "pickupCity"));
            row.put("to", ToolJson.text(delivery, "dropoffCity"));
            row.put("cashToCollect", ToolJson.amountText(delivery, "cashToCollect"));
            row.put("deliveredAt", localTime(delivery, "deliveredAt"));
            rows.add(row);
            items.add(Cards.item(id, "Delivery #" + id + " (order #" + ToolJson.number(delivery, "orderId") + ")",
                    ToolJson.text(delivery, "restaurantName"), List.of(ToolJson.text(delivery, "status")),
                    List.of(Cards.meta("To", ToolJson.text(delivery, "dropoffCity")),
                            Cards.meta("Cash", cash(delivery)),
                            Cards.meta("Delivered", localTime(delivery, "deliveredAt"))),
                    null, RoutePaths.partnerDelivery(id)));
        }
        ObjectNode summary = object();
        summary.put("totalDeliveries", page.path("totalElements").asLong(rows.size()));
        ToolJson.putCapped(summary, "deliveries", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("DELIVERY", "Your deliveries", items));
    }

    @Tool(name = "partner_my_stats", description = "How many deliveries the partner completed TODAY and in total, plus "
            + "their availability (OFFLINE, AVAILABLE, BUSY, SUSPENDED), city, vehicle and current delivery id. Needs no "
            + "arguments: today and the partner are known. The platform records no earnings.")
    @ToolPolicy(roles = Role.DELIVERY_PARTNER)
    public ToolResult partnerMyStats(ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.DELIVERY_PARTNER);
        JsonNode me = gateway.get(caller, "/api/v1/delivery/partners/me", Map.of());
        JsonNode delivered = gateway.get(caller, MINE,
                Map.of("status", "DELIVERED", "size", TODAY_LOOKUP, "sort", "deliveredAt,desc"));

        // Counted in code (the business time zone's today), never by the model
        LocalDate today = LocalDate.now(clock);
        long deliveredToday = 0;
        for (JsonNode delivery : delivered.path("content")) {
            if (today.equals(dayOf(delivery.path("deliveredAt").asString("")))) {
                deliveredToday++;
            }
        }
        ObjectNode summary = object();
        summary.put("name", ToolJson.text(me, "fullName"));
        summary.put("availability", ToolJson.text(me, "status"));
        summary.put("statusReason", ToolJson.text(me, "statusReason"));
        summary.put("city", ToolJson.text(me, "city"));
        summary.put("vehicle", ToolJson.text(me, "vehicleType") + " " + ToolJson.text(me, "vehicleNumber"));
        summary.put("completedDeliveriesTotal", me.path("completedDeliveries").asLong(0));
        summary.put("deliveredToday", deliveredToday);
        summary.put("today", today.toString());
        if (me.path("currentDeliveryId").isNumber()) {
            summary.put("currentDeliveryId", me.path("currentDeliveryId").asLong());
        }
        summary.put("earnings", "not recorded by the platform");
        Card card = Cards.stats("Your deliveries", List.of(
                Cards.stat("Status", ToolJson.text(me, "status"), ToolJson.text(me, "city")),
                Cards.stat("Delivered today", deliveredToday, today.toString()),
                Cards.stat("Delivered in total", me.path("completedDeliveries").asLong(0), null)));
        return result(summary, card);
    }

    /** One delivery as the partner needs it on the road: where to go, whom to meet, what to collect. */
    private ToolResult details(JsonNode delivery) {
        long id = ToolJson.number(delivery, "id");
        ObjectNode summary = object();
        summary.put("deliveryId", id);
        summary.put("orderId", ToolJson.number(delivery, "orderId"));
        summary.put("status", ToolJson.text(delivery, "status"));
        summary.put("restaurant", ToolJson.text(delivery, "restaurantName"));
        summary.put("pickupAddress", AddressText.line(delivery.path("pickupAddress")));
        summary.put("customer", ToolJson.text(delivery, "customerName"));
        summary.put("customerPhone", ToolJson.text(delivery, "customerPhone"));
        summary.put("dropoffAddress", AddressText.line(delivery.path("dropoffAddress")));
        summary.put("payment", ToolJson.text(delivery, "paymentMethod"));
        summary.put("cashToCollect", ToolJson.amountText(delivery, "cashToCollect") + " "
                + ToolJson.text(delivery, "currency"));
        summary.put("timeZone", clock.getZone().getId());
        summary.put("readyAt", localTime(delivery, "readyAt"));
        summary.put("acceptedAt", localTime(delivery, "acceptedAt"));
        summary.put("pickedUpAt", localTime(delivery, "pickedUpAt"));
        summary.put("deliveredAt", localTime(delivery, "deliveredAt"));
        summary.put("cancellationReason", ToolJson.text(delivery, "cancellationReason"));
        summary.put("instructions", NO_INSTRUCTIONS);
        ArrayNode steps = summary.putArray("recentSteps");
        JsonNode timeline = delivery.path("timeline");
        for (int i = Math.max(0, timeline.size() - RECENT_STEPS); i < timeline.size(); i++) {
            JsonNode step = timeline.get(i);
            String note = ToolJson.text(step, "note");
            steps.add(ToolJson.text(step, "status") + " at " + ToolJson.text(step, "at")
                    + (note.isEmpty() ? "" : " (" + note + ")"));
        }

        Card card = Cards.entities("DELIVERY", "Delivery #" + id, List.of(Cards.item(id,
                "Delivery #" + id + " (order #" + ToolJson.number(delivery, "orderId") + ")",
                ToolJson.text(delivery, "restaurantName"), List.of(ToolJson.text(delivery, "status")),
                List.of(Cards.meta("Pickup", AddressText.line(delivery.path("pickupAddress"))),
                        Cards.meta("Customer", ToolJson.text(delivery, "customerName")),
                        Cards.meta("Phone", ToolJson.text(delivery, "customerPhone")),
                        Cards.meta("Drop-off", AddressText.line(delivery.path("dropoffAddress"))),
                        Cards.meta("Cash", cash(delivery))),
                null, RoutePaths.partnerDelivery(id))));
        return result(summary, card);
    }

    /** "Paid online" or "Collect 562.90 INR" (cash on delivery). */
    private static String cash(JsonNode delivery) {
        String amount = ToolJson.amountText(delivery, "cashToCollect");
        if (amount.isEmpty() || "0".equals(amount)) {
            return "Paid online";
        }
        String currency = ToolJson.text(delivery, "currency");
        return "Collect " + amount + (currency.isEmpty() ? "" : " " + currency);
    }

    /** A moment from the API as the partner reads it: local date and time in the business time zone, or "". */
    private String localTime(JsonNode node, String field) {
        String value = node.path(field).asString("");
        try {
            return value.isBlank() ? "" : LOCAL_TIME.format(Instant.parse(value).atZone(clock.getZone()));
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    private LocalDate dayOf(String instant) {
        try {
            return instant.isBlank() ? null : LocalDate.ofInstant(Instant.parse(instant), clock.getZone());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
