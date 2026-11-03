package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.List;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.draft.OrderDraft;
import com.fooddelivery.aimcpserver.draft.OrderDraftService;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.proposal.Proposal;
import com.fooddelivery.aimcpserver.proposal.ProposalService;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The only "order" tool, and it does not order: it prepares a proposal that the customer must confirm with a button
 * (outside the model's reach). The model can never place an order.
 */
@Component
public class OrderProposalTools extends BaseTools {

    private final ProposalService proposals;
    private final OrderDraftService drafts;

    public OrderProposalTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper,
                              ProposalService proposals, OrderDraftService drafts) {
        super(gateway, callers, clock, mapper);
        this.proposals = proposals;
        this.drafts = drafts;
    }

    @Tool(name = "prepare_order", description = "Prepare an order for the customer to review, from what the customer said: "
            + "restaurant name, dish names with quantities, optionally which saved address and the payment method. Pass the "
            + "customer's own words (names), never look up or ask for ids: the system resolves them. NOTHING IS ORDERED: "
            + "this only creates a summary with Confirm and Cancel buttons that the customer must press themselves. "
            + "If the result starts with NEED_INPUT or AMBIGUOUS, relay that question to the customer in plain words "
            + "(for example which address, which payment method, which of several matching restaurants or dishes), "
            + "then call prepare_order again with their answer: what the customer said earlier is saved, so pass only "
            + "the new detail. Never claim the order is placed.")
    @ToolPolicy(roles = Role.CUSTOMER, timeoutSeconds = 25)
    public ToolResult prepareOrder(
            @ToolParam(description = "The restaurant name as the customer said it. Omit when only a later detail is new",
                    required = false) String restaurant,
            @ToolParam(description = "The dishes: up to 20 lines of dish name and quantity (1 to 20). Omit when only a "
                    + "later detail is new", required = false)
            List<OrderLine> items,
            @ToolParam(description = "The delivery address as the customer said it (label or street). Omit if not said: "
                    + "the only saved address is used, otherwise the customer is asked", required = false)
            String address,
            @ToolParam(description = "CASH_ON_DELIVERY, CARD, UPI or WALLET. Omit if the customer did not say",
                    required = false) String paymentMethod,
            @ToolParam(description = "Coupon code, only if the customer asked for one", required = false)
            String couponCode,
            @ToolParam(description = "Notes for the restaurant or rider, at most 500 characters", required = false)
            String notes,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        long userId = caller.userId();
        // What the customer said before (saved on the server) is merged with what they said now
        OrderDraft draft = drafts.merge(userId, restaurant,
                items == null ? null : items.stream().filter(line -> line != null)
                        .map(line -> new OrderDraft.Item(line.dish(), line.quantity())).toList(),
                address, paymentMethod, couponCode, notes);
        if (draft.restaurant() == null || draft.restaurant().isBlank()) {
            NeedInputException missing = new NeedInputException("NEED_INPUT: ask which restaurant the customer wants to "
                    + "order from. Ask in plain words; never ask for ids.");
            drafts.remember(userId, draft, missing.getMessage());
            throw missing;
        }
        if (draft.items().isEmpty()) {
            NeedInputException missing = new NeedInputException("NEED_INPUT: ask which dishes and how many of each. "
                    + "Ask in plain words; never ask for ids.");
            drafts.remember(userId, draft, missing.getMessage());
            throw missing;
        }
        List<ProposalService.Line> lines = draft.items().stream()
                .map(item -> new ProposalService.Line(item.dish(), null, item.quantity())).toList();
        Proposal proposal;
        try {
            proposal = proposals.prepare(caller, new ProposalService.PrepareCommand(draft.restaurant(), lines,
                    draft.address(), draft.paymentMethod(), draft.couponCode(), draft.notes()));
        } catch (NeedInputException question) {
            // The customer has to answer: keep what is known so the answer alone is enough next turn
            drafts.remember(userId, draft, question.getMessage());
            throw question;
        } catch (ToolException problem) {
            // Closed restaurant, unknown dish...: the old details no longer make sense, the customer starts again
            drafts.forget(userId);
            throw problem;
        }
        drafts.forget(userId);

        PendingAction pending = proposal.pendingAction();
        PendingAction.Summary shown = pending.summary();
        ObjectNode summary = object();
        summary.put("status", "PROPOSED_NOT_PLACED");
        summary.put("actionId", pending.actionId());
        summary.put("restaurant", shown.restaurantName());
        ArrayNode lineNodes = summary.putArray("items");
        shown.items().forEach(line -> lineNodes.add(line.name() + " x" + line.quantity()));
        summary.put("deliveryAddress", shown.deliveryAddress());
        summary.put("paymentMethod", shown.paymentMethod());
        if (shown.total() != null) {
            summary.put("total", shown.total() + " " + shown.currency());
        } else if (shown.itemsTotal() != null) {
            summary.put("itemsTotal", shown.itemsTotal().toPlainString());
        }
        ArrayNode warnings = summary.putArray("warnings");
        shown.warnings().forEach(warnings::add);
        summary.put("expiresAt", pending.expiresAt().toString());
        summary.put("instruction", "NOTHING IS ORDERED YET. Tell the customer to review the summary shown below your "
                + "answer and press Confirm to place the order or Cancel to drop it. Mention any warning.");
        return new ToolResult(summary.toString(), null, pending);
    }
}
