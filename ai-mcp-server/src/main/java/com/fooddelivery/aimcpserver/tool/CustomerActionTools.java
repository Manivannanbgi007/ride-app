package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.action.ActionBody;
import com.fooddelivery.aimcpserver.action.ActionKind;
import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Changes a customer can prepare in the chat: cancelling their own order. The customer confirms with a button. */
@Component
public class CustomerActionTools extends BaseActionTools {

    private static final String DEFAULT_REASON = "Cancelled by the customer with the assistant";

    public CustomerActionTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper,
                               ActionService actions) {
        super(gateway, callers, clock, mapper, actions);
    }

    @Tool(name = "prepare_cancel_order", description = "Prepare the cancellation of one of the customer's own orders, "
            + "by order number. Only possible before the restaurant accepts it (status PAYMENT_PENDING or PLACED). "
            + "NOTHING IS CANCELLED: this shows a summary with a button the customer must press. "
            + CALL_AT_ONCE + " Never say it is cancelled.")
    @ToolPolicy(roles = Role.CUSTOMER, timeoutSeconds = 15)
    public ToolResult prepareCancelOrder(
            @ToolParam(description = "The order number") Long orderId,
            @ToolParam(description = "Why, in the customer's words, if they said it", required = false) String reason,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        long id = ToolArgs.positiveId("orderId", orderId);
        String why = ToolArgs.optionalText("reason", reason, MAX_REASON);
        // order-service answers 404 for another customer's order
        JsonNode order = gateway.get(caller, "/api/v1/orders/" + id, Map.of());
        String status = order.path("status").asString("");
        if (!OrderStatus.PAYMENT_PENDING.name().equals(status) && !OrderStatus.PLACED.name().equals(status)) {
            throw notNow("Order #" + id, status, "it can no longer be cancelled here: the restaurant has already "
                    + "accepted it (or it is finished). The customer can contact support from the order page.");
        }
        String note;
        if (paidOnline(order)) {
            note = "The amount paid online is refunded automatically to the same payment method.";
        } else if (OrderStatus.PAYMENT_PENDING.name().equals(status)) {
            note = "The payment was not completed, so nothing is charged.";
        } else {
            note = null;
        }
        ActionService.Plan plan = new ActionService.Plan(ActionKind.CANCEL_MY_ORDER, null, id,
                ActionBody.reason(why == null ? DEFAULT_REASON : why),
                details("Cancel order #" + id, List.of(
                        row("Restaurant", ToolJson.text(order, "restaurantName")),
                        row("Items", itemsText(order)),
                        row("Total", totalText(order)),
                        row("Payment", paymentText(order)),
                        row("Reason", why == null ? "" : why)), note, "Cancel order"),
                "Order #" + id + " has been cancelled." + (paidOnline(order) ? " The refund has been started." : ""),
                RoutePaths.customerOrder(id));
        return prepared(caller, plan);
    }
}
