package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpserver.action.ActionBody;
import com.fooddelivery.aimcpserver.action.ActionKind;
import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.client.GatewayException;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.NameMatcher;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import com.fooddelivery.aimcpserver.support.ToolText;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Changes a restaurant owner can prepare in the chat: the next step of an order and whether a dish can be ordered.
 * The order or dish is looked up in the owner's own restaurants only (the services check ownership again).
 */
@Component
public class OwnerActionTools extends BaseActionTools {

    private static final String MINE = "/api/v1/restaurants/mine/";
    private static final Pattern ID = Pattern.compile("\\d{1,18}");
    private static final int MAX_OPTIONS = 8;

    /** What the owner can do with an order, and the status it must have. */
    enum OrderStep {
        ACCEPT(ActionKind.ACCEPT_ORDER, OrderStatus.PLACED, "Accept order #", "Accept order", "accepted"),
        REJECT(ActionKind.REJECT_ORDER, OrderStatus.PLACED, "Reject order #", "Reject order", "rejected"),
        START_PREPARING(ActionKind.START_PREPARING, OrderStatus.ACCEPTED, "Start preparing order #", "Start preparing",
                "being prepared"),
        MARK_READY(ActionKind.MARK_READY, OrderStatus.PREPARING, "Mark ready for pickup: order #", "Mark ready",
                "ready for pickup");

        private final ActionKind kind;
        private final OrderStatus from;
        private final String title;
        private final String button;
        private final String done;

        OrderStep(ActionKind kind, OrderStatus from, String title, String button, String done) {
            this.kind = kind;
            this.from = from;
            this.title = title;
            this.button = button;
            this.done = done;
        }
    }

    public OwnerActionTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper,
                            ActionService actions) {
        super(gateway, callers, clock, mapper, actions);
    }

    @Tool(name = "owner_prepare_order_step", description = "Prepare the next step of an order of the owner's restaurant: "
            + "ACCEPT or REJECT a new order (PLACED), START_PREPARING an accepted order, MARK_READY an order being "
            + "prepared (a rider is then sent). REJECT needs a reason, which the customer sees. Finds the order in all "
            + "of the owner's restaurants. NOTHING CHANGES: this shows a summary with a button the owner must press. "
            + CALL_AT_ONCE + " Never say it is done.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN, timeoutSeconds = 15)
    public ToolResult ownerPrepareOrderStep(
            @ToolParam(description = "The order number") Long orderId,
            @ToolParam(description = "ACCEPT, REJECT, START_PREPARING or MARK_READY") String step,
            @ToolParam(description = "Why the order is rejected (REJECT only), in the owner's words", required = false)
            String reason,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        long id = ToolArgs.positiveId("orderId", orderId);
        OrderStep wanted = ToolArgs.requiredEnum("step", step, OrderStep.class);
        String why = wanted == OrderStep.REJECT
                ? requiredReason(reason, "ask the owner why they reject order #" + id + " (the customer sees it).")
                : null;
        OwnedOrder found = findOwnedOrder(caller, id);
        JsonNode order = found.order();
        String status = order.path("status").asString("");
        if (!wanted.from.name().equals(status)) {
            throw notNow("Order #" + id, status, "it cannot be " + wanted.done + " now (that needs status "
                    + wanted.from + ").");
        }
        String note = switch (wanted) {
            case REJECT -> "The customer sees the reason. An amount paid online is refunded automatically.";
            case MARK_READY -> "A delivery partner is then sent to pick it up.";
            default -> null;
        };
        ActionService.Plan plan = new ActionService.Plan(wanted.kind, found.restaurantId(), id,
                why == null ? ActionBody.NONE : ActionBody.reason(why),
                details(wanted.title + id, List.of(
                        row("Customer", ToolJson.text(order, "customerName")),
                        row("Items", itemsText(order)),
                        row("Total", totalText(order)),
                        row("Payment", paymentText(order)),
                        row("Status now", status),
                        row("Reason", why == null ? "" : why)), note, wanted.button),
                "Order #" + id + " is " + wanted.done + ".", RoutePaths.ownerOrder(id));
        return prepared(caller, plan);
    }

    @Tool(name = "owner_prepare_item_availability", description = "Prepare marking a dish of the owner's menu as sold "
            + "out (available=false: customers cannot order it, it stays on the menu) or available again "
            + "(available=true). Pass the dish name as the owner said it; the dish is found in all of the owner's "
            + "restaurants. NOTHING CHANGES: this shows a summary with a button the owner must press. "
            + CALL_AT_ONCE + " Never say it is done.")
    @ToolPolicy(roles = Role.RESTAURANT_ADMIN, timeoutSeconds = 15)
    public ToolResult ownerPrepareItemAvailability(
            @ToolParam(description = "The dish name as the owner said it (or its menu item id)") String dish,
            @ToolParam(description = "false = sold out, true = available again") Boolean available,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.RESTAURANT_ADMIN);
        String wanted = ToolArgs.optionalText("dish", dish, 100);
        if (wanted == null) {
            throw ToolException.invalid("dish is required: the dish name the owner said.");
        }
        if (available == null) {
            throw ToolException.invalid("available is required: false for sold out, true for available again.");
        }
        MenuDish item = findDish(caller, wanted);
        String name = ToolJson.text(item.item(), "name");
        boolean now = item.item().path("available").asBoolean(false);
        if (now == available) {
            ObjectNode summary = object();
            summary.put("status", "NO_CHANGE_NEEDED");
            summary.put("dish", name);
            summary.put("available", now);
            summary.put("note", (now ? "It is already available." : "It is already marked sold out.")
                    + " Nothing to change.");
            return result(summary, null);
        }
        String title = available ? "Mark " + name + " available again" : "Mark " + name + " sold out";
        ActionService.Plan plan = new ActionService.Plan(ActionKind.SET_ITEM_AVAILABILITY, item.restaurantId(),
                ToolJson.number(item.item(), "id"), new ActionBody(null, available, null, null),
                details(title, List.of(
                        row("Dish", name),
                        row("Restaurant", item.restaurantName()),
                        row("Price", money(item.item(), "price", "")),
                        row("Now", now ? "Available" : "Sold out"),
                        row("After", available ? "Available" : "Sold out")),
                        available ? "Customers can order it again right away."
                                : "Customers cannot order it until you mark it available again. It stays on the menu.",
                        available ? "Mark available" : "Mark sold out"),
                name + (available ? " is available again." : " is marked sold out."), RoutePaths.ownerMenu());
        return prepared(caller, plan);
    }

    private record OwnedOrder(long restaurantId, JsonNode order) {
    }

    private record MenuDish(long restaurantId, String restaurantName, JsonNode item) {
    }

    /** The order in one of the owner's restaurants; another restaurant's order is never read. */
    private OwnedOrder findOwnedOrder(CallerContext caller, long orderId) {
        for (long restaurantId : ownedRestaurantIds(caller)) {
            try {
                return new OwnedOrder(restaurantId,
                        gateway.get(caller, MINE + restaurantId + "/orders/" + orderId, Map.of()));
            } catch (GatewayException e) {
                if (e.getStatus() != 404 && e.getStatus() != 403) {
                    throw e;
                }
            }
        }
        throw ToolException.invalid("Order #" + orderId + " is not an order of your restaurants.");
    }

    /** The dish by name (or id) across the owner's restaurants; several matches become a question. */
    private MenuDish findDish(CallerContext caller, String wanted) {
        JsonNode mine = gateway.get(caller, "/api/v1/restaurants/mine", Map.of());
        List<MenuDish> dishes = new ArrayList<>();
        for (JsonNode restaurant : mine) {
            long restaurantId = ToolJson.number(restaurant, "id");
            String restaurantName = ToolJson.text(restaurant, "name");
            gateway.get(caller, MINE + restaurantId + "/menu-items", Map.of())
                    .forEach(item -> dishes.add(new MenuDish(restaurantId, restaurantName, item)));
        }
        List<MenuDish> matches = ID.matcher(wanted).matches()
                ? dishes.stream().filter(d -> d.item().path("id").asLong(0) == Long.parseLong(wanted)).toList()
                : NameMatcher.match(wanted, dishes, d -> d.item().path("name").asString(""));
        if (matches.isEmpty()) {
            throw ToolException.invalid("No dish of your menu matches \"" + ToolText.clean(wanted, 60)
                    + "\". owner_menu lists your dishes.");
        }
        if (matches.size() > 1) {
            List<String> options = matches.stream().limit(MAX_OPTIONS)
                    .map(d -> ToolJson.text(d.item(), "name") + " (" + d.restaurantName() + ")").toList();
            throw new NeedInputException("AMBIGUOUS: several dishes match. Ask the owner which one they mean: "
                    + options + ".");
        }
        return matches.get(0);
    }
}
