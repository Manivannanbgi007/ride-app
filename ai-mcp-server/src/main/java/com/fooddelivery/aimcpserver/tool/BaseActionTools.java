package com.fooddelivery.aimcpserver.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fooddelivery.aimcpserver.action.ActionService;
import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The tools that prepare a change (cancel an order, accept it, mark a delivery picked up...). They read and check
 * first, then store the change with ActionService.propose: NOTHING CHANGES until the user presses Confirm, which the
 * model cannot do (no tool reaches ActionService.confirm).
 */
public abstract class BaseActionTools extends BaseTools {

    protected static final int MAX_REASON = 300;
    /**
     * In every prepare tool's description: a small model otherwise asks "Shall I...?" before preparing, although
     * preparing changes nothing and the button already asks the user.
     */
    protected static final String CALL_AT_ONCE = "Call it as soon as the user asks for this change; never ask them to "
            + "confirm first: the button is the confirmation.";
    private static final int MAX_ITEMS_TEXT = 160;

    protected final ActionService actions;

    protected BaseActionTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper,
                              ActionService actions) {
        super(gateway, callers, clock, mapper);
        this.actions = actions;
    }

    /** Stores the change and tells the model it is only prepared; the Confirm card comes with the answer. */
    protected ToolResult prepared(CallerContext caller, ActionService.Plan plan) {
        PendingAction pending = actions.propose(caller, plan);
        PendingAction.Details details = pending.details();
        ObjectNode summary = object();
        summary.put("status", "PREPARED_NOT_DONE");
        summary.put("actionId", pending.actionId());
        summary.put("change", details.title());
        ArrayNode rows = summary.putArray("details");
        details.rows().forEach(row -> rows.add(row.label() + ": " + row.value()));
        if (details.note() != null) {
            summary.put("note", details.note());
        }
        summary.put("expiresAt", pending.expiresAt().toString());
        summary.put("instruction", "NOTHING HAS CHANGED YET. Tell the user to check the summary shown below your answer "
                + "and press \"" + details.confirmLabel() + "\" to do it, or dismiss it. Mention the note if there is "
                + "one. Never say it is done.");
        return new ToolResult(summary.toString(), null, pending);
    }

    protected static PendingAction.Details details(String title, List<PendingAction.Row> rows, String note,
                                                   String confirmLabel) {
        return new PendingAction.Details(title, rows.stream().filter(row -> !row.value().isBlank()).toList(), note,
                confirmLabel);
    }

    protected static PendingAction.Row row(String label, String value) {
        return new PendingAction.Row(label, value == null ? "" : value);
    }

    /** A reason the business API requires: asked for (NEED_INPUT) when missing, refused when too long. */
    protected static String requiredReason(String reason, String question) {
        String text = ToolArgs.optionalText("reason", reason, MAX_REASON);
        if (text == null) {
            throw new NeedInputException("NEED_INPUT: " + question + " Ask in plain words, then call this tool again "
                    + "with the same details and the reason.");
        }
        return text;
    }

    /** "Paneer Tikka x2, Garlic Naan x1" from an order's items, cut to a sensible length. */
    protected static String itemsText(JsonNode order) {
        List<String> lines = new ArrayList<>();
        for (JsonNode item : order.path("items")) {
            lines.add(ToolJson.text(item, "name") + " x" + item.path("quantity").asInt(0));
        }
        String text = String.join(", ", lines);
        return text.length() > MAX_ITEMS_TEXT ? text.substring(0, MAX_ITEMS_TEXT - 3) + "..." : text;
    }

    /** "562.90 INR" from an order's price. */
    protected static String totalText(JsonNode order) {
        JsonNode price = order.path("price");
        return money(price, "totalAmount", ToolJson.text(price, "currency"));
    }

    /** An amount as money is written ("562.90 INR"), or "" when it is missing. */
    protected static String money(JsonNode node, String field, String currency) {
        BigDecimal amount = ToolJson.amount(node, field);
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + (currency.isEmpty() ? "" : " " + currency);
    }

    /** "UPI, paid" / "Cash on delivery". */
    protected static String paymentText(JsonNode order) {
        String method = ToolJson.text(order, "paymentMethod");
        if ("CASH_ON_DELIVERY".equals(method)) {
            return "Cash on delivery";
        }
        String status = ToolJson.text(order, "paymentStatus");
        return method + (status.isEmpty() ? "" : ", " + status.toLowerCase(Locale.ROOT));
    }

    protected static boolean paidOnline(JsonNode order) {
        return "PAID".equals(order.path("paymentStatus").asString(""))
                && !"CASH_ON_DELIVERY".equals(order.path("paymentMethod").asString(""));
    }

    protected static ToolException notNow(String what, String status, String why) {
        return ToolException.invalid(what + " is " + status + ", so " + why);
    }
}
