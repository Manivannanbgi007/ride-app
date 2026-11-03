package com.fooddelivery.aimcpserver.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.action.ActionBody;
import com.fooddelivery.aimcpserver.action.ActionKind;
import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.AddressText;
import com.fooddelivery.aimcpserver.support.ToolException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The delivery partner's steps, prepared in the chat and confirmed with a button: accept or decline an offer, picked
 * up, delivered. Only the partner's own deliveries are read (delivery-service answers 404 for anyone else's).
 */
@Component
public class PartnerActionTools extends BaseActionTools {

    private static final String MINE = "/api/v1/delivery/deliveries/mine";

    /** A step of the partner, and the status the delivery must have. */
    enum DeliveryStep {
        ACCEPT(ActionKind.ACCEPT_DELIVERY, DeliveryStatus.ASSIGNED, "Accept delivery #", "Accept delivery", "accepted"),
        DECLINE(ActionKind.DECLINE_DELIVERY, DeliveryStatus.ASSIGNED, "Decline delivery #", "Decline delivery",
                "declined"),
        PICKED_UP(ActionKind.MARK_PICKED_UP, DeliveryStatus.ACCEPTED, "Picked up: delivery #", "Picked up",
                "marked picked up"),
        DELIVERED(ActionKind.MARK_DELIVERED, DeliveryStatus.PICKED_UP, "Delivered: delivery #", "Delivered",
                "marked delivered");

        private final ActionKind kind;
        private final DeliveryStatus from;
        private final String title;
        private final String button;
        private final String done;

        DeliveryStep(ActionKind kind, DeliveryStatus from, String title, String button, String done) {
            this.kind = kind;
            this.from = from;
            this.title = title;
            this.button = button;
            this.done = done;
        }
    }

    public PartnerActionTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper,
                              ActionService actions) {
        super(gateway, callers, clock, mapper, actions);
    }

    @Tool(name = "partner_prepare_delivery_step", description = "Prepare the partner's next step on a delivery: ACCEPT "
            + "or DECLINE an offer (ASSIGNED), PICKED_UP once the food is collected (ACCEPTED), DELIVERED once the "
            + "customer has it (PICKED_UP; for cash on delivery the exact cash must be collected first). Without "
            + "deliveryId the partner's current delivery is used. NOTHING CHANGES: this shows a summary with a button "
            + "the partner must press. " + CALL_AT_ONCE + " Never say it is done.")
    @ToolPolicy(roles = Role.DELIVERY_PARTNER, timeoutSeconds = 15)
    public ToolResult partnerPrepareDeliveryStep(
            @ToolParam(description = "ACCEPT, DECLINE, PICKED_UP or DELIVERED") String step,
            @ToolParam(description = "The delivery id (not the order id). Omit for the current delivery",
                    required = false) Long deliveryId,
            @ToolParam(description = "Why the offer is declined (DECLINE only), if the partner said it",
                    required = false) String reason,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.DELIVERY_PARTNER);
        DeliveryStep wanted = ToolArgs.requiredEnum("step", step, DeliveryStep.class);
        String why = wanted == DeliveryStep.DECLINE ? ToolArgs.optionalText("reason", reason, MAX_REASON) : null;
        JsonNode delivery = deliveryId == null
                ? gateway.get(caller, MINE + "/current", Map.of())
                : gateway.get(caller, MINE + "/" + ToolArgs.positiveId("deliveryId", deliveryId), Map.of());
        if (delivery.path("id").isMissingNode()) {
            throw ToolException.invalid("The partner has no current delivery right now, so nothing can be "
                    + wanted.done + ".");
        }
        long id = ToolJson.number(delivery, "id");
        String status = delivery.path("status").asString("");
        if (!wanted.from.name().equals(status)) {
            throw notNow("Delivery #" + id, status, "it cannot be " + wanted.done + " now (that needs status "
                    + wanted.from + ").");
        }
        String currency = ToolJson.text(delivery, "currency");
        BigDecimal due = ToolJson.amount(delivery, "cashToCollect");
        boolean collectsCash = due != null && due.signum() > 0;
        // Money has two decimals; the JSON number may have been read as a double
        BigDecimal cash = collectsCash ? due.setScale(2, RoundingMode.HALF_UP) : null;
        String cashText = collectsCash ? "Collect " + money(delivery, "cashToCollect", currency) : "Paid online";

        ActionBody body = switch (wanted) {
            case DECLINE -> why == null ? ActionBody.NONE : ActionBody.reason(why);
            // The service checks the amount equals what is due: the partner confirms they collected exactly that
            case DELIVERED -> collectsCash ? new ActionBody(null, null, null, cash) : ActionBody.NONE;
            default -> ActionBody.NONE;
        };
        String note = switch (wanted) {
            case ACCEPT -> "Then go to the restaurant to pick up the food.";
            case DECLINE -> "The offer goes to another partner.";
            case PICKED_UP -> "Confirm only once you have the food.";
            case DELIVERED -> collectsCash
                    ? "Confirm only after collecting exactly " + money(delivery, "cashToCollect", currency) + " in cash."
                    : "Confirm only once the customer has the food.";
        };
        ActionService.Plan plan = new ActionService.Plan(wanted.kind, null, id, body,
                details(wanted.title + id + " (order #" + ToolJson.number(delivery, "orderId") + ")", List.of(
                        row("Restaurant", ToolJson.text(delivery, "restaurantName")),
                        row("Pickup", AddressText.line(delivery.path("pickupAddress"))),
                        row("Customer", ToolJson.text(delivery, "customerName")),
                        row("Drop-off", AddressText.line(delivery.path("dropoffAddress"))),
                        row("Cash", cashText),
                        row("Reason", why == null ? "" : why)), note, wanted.button),
                "Delivery #" + id + " is " + wanted.done + ".", RoutePaths.partnerDelivery(id));
        return prepared(caller, plan);
    }
}
