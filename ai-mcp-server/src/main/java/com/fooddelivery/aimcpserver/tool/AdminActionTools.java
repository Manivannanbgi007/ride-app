package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpserver.action.ActionBody;
import com.fooddelivery.aimcpserver.action.ActionKind;
import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.client.GatewayClient;
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

/**
 * Platform admin interventions prepared in the chat and confirmed with a button: cancel an order, reassign a delivery.
 * Both are audited by the business services too (with the admin's reason).
 */
@Component
public class AdminActionTools extends BaseActionTools {

    private static final Pattern ID = Pattern.compile("\\d{1,18}");
    private static final Set<String> CANCELLABLE = Set.of(OrderStatus.PAYMENT_PENDING.name(),
            OrderStatus.PLACED.name(), OrderStatus.ACCEPTED.name(), OrderStatus.PREPARING.name(),
            OrderStatus.READY_FOR_PICKUP.name());
    private static final Set<String> REASSIGNABLE = Set.of(DeliveryStatus.PENDING.name(),
            DeliveryStatus.ASSIGNED.name(), DeliveryStatus.ACCEPTED.name());
    private static final String PARTNERS = "/api/v1/admin/delivery-partners";
    private static final int MAX_OPTIONS = 8;

    public AdminActionTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper,
                            ActionService actions) {
        super(gateway, callers, clock, mapper, actions);
    }

    @Tool(name = "admin_prepare_cancel_order", description = "Prepare cancelling any order that is not out for delivery "
            + "yet (PAYMENT_PENDING, PLACED, ACCEPTED, PREPARING or READY_FOR_PICKUP). Needs the admin's reason (audit "
            + "log). NOTHING IS CANCELLED: this shows a summary with a button the admin must press. "
            + CALL_AT_ONCE + " Never say it is cancelled.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN, timeoutSeconds = 15)
    public ToolResult adminPrepareCancelOrder(
            @ToolParam(description = "The order number") Long orderId,
            @ToolParam(description = "Why, in the admin's words (stored in the audit log)", required = false)
            String reason,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        long id = ToolArgs.positiveId("orderId", orderId);
        String why = requiredReason(reason, "ask the admin why order #" + id + " is cancelled (kept in the audit log).");
        JsonNode order = gateway.get(caller, "/api/v1/admin/orders/" + id, Map.of());
        String status = order.path("status").asString("");
        if (!CANCELLABLE.contains(status)) {
            throw notNow("Order #" + id, status, "it can no longer be cancelled (only orders that are not out for "
                    + "delivery or finished can be).");
        }
        ActionService.Plan plan = new ActionService.Plan(ActionKind.ADMIN_CANCEL_ORDER, null, id,
                ActionBody.reason(why),
                details("Cancel order #" + id, List.of(
                        row("Customer", ToolJson.text(order, "customerName")),
                        row("Restaurant", ToolJson.text(order, "restaurantName")),
                        row("Total", totalText(order)),
                        row("Payment", paymentText(order)),
                        row("Status now", status),
                        row("Reason", why)),
                        paidOnline(order) ? "The amount paid online is refunded automatically. Recorded in the audit "
                                + "log with your reason." : "Recorded in the audit log with your reason.",
                        "Cancel order"),
                "Order #" + id + " has been cancelled." + (paidOnline(order) ? " The refund has been started." : ""),
                RoutePaths.adminOrder(id));
        return prepared(caller, plan);
    }

    @Tool(name = "admin_prepare_reassign_delivery", description = "Prepare reassigning a delivery that is not picked up "
            + "yet (PENDING, ASSIGNED or ACCEPTED): to a given AVAILABLE delivery partner (name or id), or without a "
            + "partner back to the queue so the next available partner gets it. Needs the admin's reason. NOTHING "
            + "CHANGES: this shows a summary with a button the admin must press. " + CALL_AT_ONCE
            + " Never say it is done.")
    @ToolPolicy(roles = Role.PLATFORM_ADMIN, timeoutSeconds = 15)
    public ToolResult adminPrepareReassignDelivery(
            @ToolParam(description = "The delivery id (not the order id)") Long deliveryId,
            @ToolParam(description = "The partner to give it to: name or user id. Omit to put it back in the queue",
                    required = false) String partner,
            @ToolParam(description = "Why, in the admin's words (stored in the audit log)", required = false)
            String reason,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.PLATFORM_ADMIN);
        long id = ToolArgs.positiveId("deliveryId", deliveryId);
        String why = requiredReason(reason, "ask the admin why delivery #" + id + " is reassigned (kept in the audit "
                + "log).");
        JsonNode delivery = gateway.get(caller, "/api/v1/admin/deliveries/" + id, Map.of());
        String status = delivery.path("status").asString("");
        if (!REASSIGNABLE.contains(status)) {
            throw notNow("Delivery #" + id, status, "it can no longer be reassigned (only deliveries that are not "
                    + "picked up yet can be).");
        }
        long currentPartner = delivery.path("partnerUserId").asLong(0);
        String now = currentPartner == 0 ? "Nobody (waiting for a partner)"
                : ToolJson.text(delivery, "partnerName") + " (id " + currentPartner + ")";

        String wanted = ToolArgs.optionalText("partner", partner, 100);
        JsonNode next = wanted == null ? null : findAvailablePartner(caller, wanted);
        if (next == null && currentPartner == 0) {
            throw ToolException.invalid("Delivery #" + id + " is already waiting for a partner. Say which available "
                    + "partner should get it.");
        }
        if (next != null && ToolJson.number(next, "userId") == currentPartner) {
            throw ToolException.invalid("Delivery #" + id + " is already with " + ToolJson.text(next, "fullName") + ".");
        }
        String after = next == null ? "Back in the queue: the next available partner gets it"
                : ToolJson.text(next, "fullName") + " (id " + ToolJson.number(next, "userId") + ", "
                + ToolJson.text(next, "city") + ")";
        ActionService.Plan plan = new ActionService.Plan(ActionKind.REASSIGN_DELIVERY, null, id,
                new ActionBody(why, null, next == null ? null : ToolJson.number(next, "userId"), null),
                details("Reassign delivery #" + id + " (order #" + ToolJson.number(delivery, "orderId") + ")", List.of(
                        row("Restaurant", ToolJson.text(delivery, "restaurantName")),
                        row("Status now", status),
                        row("Partner now", now),
                        row("Partner after", after),
                        row("Reason", why)),
                        next == null ? "Recorded in the audit log with your reason."
                                : "The new partner gets the offer and has to accept it. Recorded in the audit log.",
                        "Reassign"),
                "Delivery #" + id + " has been reassigned.", RoutePaths.adminDelivery(id));
        return prepared(caller, plan);
    }

    /** The partner by id or name; only an AVAILABLE partner can take a delivery. */
    private JsonNode findAvailablePartner(CallerContext caller, String wanted) {
        JsonNode found;
        if (ID.matcher(wanted).matches()) {
            found = gateway.get(caller, PARTNERS + "/" + wanted, Map.of());
        } else {
            List<JsonNode> candidates = new ArrayList<>();
            gateway.get(caller, PARTNERS, Map.of("q", wanted, "size", 20)).path("content").forEach(candidates::add);
            List<JsonNode> matches = NameMatcher.match(wanted, candidates, p -> p.path("fullName").asString(""));
            if (matches.isEmpty()) {
                matches = candidates;
            }
            if (matches.isEmpty()) {
                throw ToolException.invalid("No delivery partner matches \"" + ToolText.clean(wanted, 60) + "\".");
            }
            if (matches.size() > 1) {
                List<String> options = matches.stream().limit(MAX_OPTIONS)
                        .map(p -> ToolJson.text(p, "fullName") + " (id " + ToolJson.number(p, "userId") + ", "
                                + ToolJson.text(p, "city") + ", " + ToolJson.text(p, "status") + ")").toList();
                throw new NeedInputException("AMBIGUOUS: several partners match. Ask the admin which one they mean: "
                        + options + ".");
            }
            found = matches.get(0);
        }
        String status = found.path("status").asString("");
        if (!"AVAILABLE".equals(status)) {
            throw ToolException.invalid(ToolJson.text(found, "fullName") + " is " + status
                    + ": only an AVAILABLE partner (online, without a delivery) can get it.");
        }
        return found;
    }
}
