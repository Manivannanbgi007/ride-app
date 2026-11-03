package com.fooddelivery.aimcpserver.proposal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.client.GatewayException;
import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.dto.ConfirmResponse;
import com.fooddelivery.aimcpserver.dto.Link;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.exception.ApiException;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.support.AuditLog;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import com.fooddelivery.aimcpserver.support.ToolText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * The only state change the assistant can cause, and only in two separate steps:
 * prepare (called by the prepare_order tool: checks everything, stores a proposal, orders nothing) and
 * confirm (called by the BFF after the user pressed Confirm: places the order exactly once).
 */
@Slf4j
@Service
public class ProposalService {

    private static final Pattern COUPON = Pattern.compile("[A-Za-z0-9_-]{3,30}");
    private static final int MAX_NOTES = 500;
    private static final long CONFIRM_WAIT_MILLIS = 15_000;
    private static final long CONFIRM_POLL_MILLIS = 100;
    private static final String CART = "/api/v1/cart";
    private static final String UNAVAILABLE = "Order confirmation is temporarily unavailable. Please try again in a moment.";

    private final GatewayClient gateway;
    private final ProposalStore store;
    private final AppProperties properties;
    private final Clock clock;
    private final OrderResolver resolver;

    public ProposalService(GatewayClient gateway, ProposalStore store, AppProperties properties, Clock clock) {
        this.gateway = gateway;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.resolver = new OrderResolver(gateway);
    }

    /**
     * One requested dish as the customer said it: dish is a name (or a numeric id); menuItemId is accepted too.
     */
    public record Line(String dish, Long menuItemId, Integer quantity) {
    }

    /**
     * What the customer asked for, as given by the tool (still unchecked). restaurant and address are the customer's
     * words (name, label) or numeric ids; address and paymentMethod may be missing.
     */
    public record PrepareCommand(String restaurant, List<Line> items, String address, String paymentMethod,
                                 String couponCode, String notes) {
    }

    // ---------------------------------------------------------------- prepare

    /**
     * Resolves names to ids in code, validates, prices when it is safe to, stores the proposal and returns it.
     * Throws ToolException with a clear message, or NeedInputException when the customer must answer a question.
     */
    public Proposal prepare(CallerContext caller, PrepareCommand command) {
        String coupon = couponOf(command.couponCode());
        String notes = notesOf(command.notes());
        PaymentMethod payment = paymentOf(command.paymentMethod());

        long restaurantId = resolver.restaurantId(caller, command.restaurant());
        JsonNode restaurant = gateway.get(caller, "/api/v1/restaurants/" + restaurantId, Map.of());
        String restaurantName = ToolText.clean(restaurant.path("name").asString(""), 80);
        if (!restaurant.path("openNow").asBoolean(false)) {
            throw ToolException.invalid(restaurantName + " is closed right now, so an order cannot be placed.");
        }
        Map<Long, JsonNode> menu = menuOf(caller, restaurantId);
        Map<Long, Integer> wanted = resolver.dishes(command.items(), menu, restaurantName);
        List<String> needs = new ArrayList<>();
        JsonNode address = resolver.address(caller, command.address(), needs);
        if (payment == null) {
            needs.add("ask how the customer wants to pay, options: " + Arrays.toString(PaymentMethod.values()));
        }
        if (!needs.isEmpty()) {
            throw new NeedInputException("NEED_INPUT: " + String.join("; ", needs)
                    + ". Ask the customer in plain words; never ask for ids.");
        }
        List<Proposal.Line> lines = new ArrayList<>();
        List<PendingAction.Line> shown = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            JsonNode item = menu.get(entry.getKey());
            BigDecimal price = item.path("price").isNumber() ? item.path("price").decimalValue() : null;
            lines.add(new Proposal.Line(entry.getKey(), entry.getValue()));
            shown.add(new PendingAction.Line(ToolText.clean(item.path("name").asString(""), 80), entry.getValue(), price,
                    price == null ? null : price.multiply(BigDecimal.valueOf(entry.getValue()))));
        }
        long addressId = address.path("id").asLong(0);
        String addressLine = addressLineOf(address);

        List<String> warnings = new ArrayList<>();
        PendingAction.Summary summary = priced(caller, lines, shown, coupon, restaurantName, addressLine, payment, notes,
                warnings);
        if (payment != PaymentMethod.CASH_ON_DELIVERY) {
            warnings.add("This is an online payment: after confirming you will be asked to pay for the order.");
        }

        Instant now = Instant.now(clock);
        Proposal proposal = new Proposal(UUID.randomUUID().toString(), caller.userId(), Proposal.Status.NEW, now,
                now.plus(properties.proposal().ttl()), restaurantId, List.copyOf(lines), addressId, payment.name(),
                coupon, notes, summary, null, null, null, null, null);
        try {
            store.save(proposal, properties.proposal().ttl().plus(properties.proposal().keepAfterExpiry()));
        } catch (ProposalStoreException e) {
            log.warn("Proposal not saved: {}", e.getMessage());
            throw new ToolException(ToolException.Kind.UNAVAILABLE,
                    "The order could not be prepared right now (temporary problem). Please try again in a moment.");
        }
        log.info("proposal prepared actionId={} lines={}", proposal.id(), lines.size());
        audit("proposed", proposal, caller, "ok");
        return proposal;
    }

    private static PaymentMethod paymentOf(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ToolException.invalid("paymentMethod must be one of: " + Arrays.toString(PaymentMethod.values()) + ".");
        }
    }

    private static String addressLineOf(JsonNode address) {
        List<String> parts = new ArrayList<>();
        for (String field : new String[] {"line1", "line2", "city", "postalCode"}) {
            String part = ToolText.clean(address.path(field).asString(""), 80);
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return String.join(", ", parts);
    }

    /**
     * The real price comes from the order-service cart, but the cart is the customer's own: it is used (and emptied
     * again) only when it is empty. Otherwise the proposal shows the menu prices and says fees come at checkout.
     */
    private PendingAction.Summary priced(CallerContext caller, List<Proposal.Line> lines, List<PendingAction.Line> shown,
                                         String coupon, String restaurantName, String addressLine, PaymentMethod payment,
                                         String notes, List<String> warnings) {
        JsonNode cart = gateway.get(caller, CART, Map.of());
        if (cart.path("items").size() > 0) {
            warnings.add("Your cart is not empty: it will be replaced by this order when you confirm. "
                    + "The delivery fee, tax and any discount are calculated at checkout.");
            if (coupon != null) {
                warnings.add("Coupon " + coupon.toUpperCase(Locale.ROOT) + " is checked when you confirm.");
            }
            BigDecimal itemsTotal = shown.stream().map(PendingAction.Line::lineTotal)
                    .reduce(BigDecimal.ZERO, (a, b) -> a == null || b == null ? null : a.add(b));
            return new PendingAction.Summary(restaurantName, shown, addressLine, payment.name(), itemsTotal, null, null,
                    null, null, null, notes, warnings);
        }
        boolean priced = false;
        try {
            for (Proposal.Line line : lines) {
                gateway.put(caller, CART + "/items/" + line.menuItemId(), Map.of("quantity", line.quantity()));
            }
            if (coupon != null) {
                applyCoupon(caller, coupon);
            }
            JsonNode priceCart = gateway.get(caller, CART, Map.of());
            if (!priceCart.path("orderable").asBoolean(true)) {
                throw ToolException.invalid("The order cannot be placed yet: " + problemsOf(priceCart));
            }
            priced = true;
            return summaryFromCart(priceCart, restaurantName, addressLine, payment, notes, warnings, shown);
        } finally {
            clearCartQuietly(caller, priced ? warnings : null);
        }
    }

    private void applyCoupon(CallerContext caller, String coupon) {
        try {
            gateway.put(caller, CART + "/coupon", Map.of("code", coupon));
        } catch (GatewayException e) {
            if (e.isTransientFailure()) {
                throw e;
            }
            throw ToolException.invalid("Coupon " + coupon.toUpperCase(Locale.ROOT) + " cannot be used: "
                    + e.getMessage());
        }
    }

    private PendingAction.Summary summaryFromCart(JsonNode cart, String restaurantName, String addressLine,
                                                  PaymentMethod payment, String notes, List<String> warnings,
                                                  List<PendingAction.Line> fallback) {
        JsonNode price = cart.path("price");
        List<PendingAction.Line> items = new ArrayList<>();
        for (JsonNode item : cart.path("items")) {
            int quantity = item.path("quantity").asInt(0);
            items.add(new PendingAction.Line(ToolText.clean(item.path("name").asString(""), 80), quantity,
                    decimal(item, "unitPrice"), decimal(item, "lineTotal")));
        }
        JsonNode coupon = cart.path("coupon");
        if (coupon.isObject() && !coupon.path("applied").asBoolean(true)) {
            warnings.add("The coupon is not applied: " + ToolText.clean(coupon.path("reason").asString(""), 120));
        }
        return new PendingAction.Summary(restaurantName, items.isEmpty() ? fallback : items, addressLine, payment.name(),
                decimal(price, "itemsTotal"), decimal(price, "discountAmount"), decimal(price, "deliveryFee"),
                decimal(price, "taxAmount"), decimal(price, "totalAmount"), ToolText.clean(price.path("currency")
                        .asString(""), 8), notes, warnings);
    }

    // The cart must be left empty: a failure here is logged and, when the price was fine, mentioned to the customer
    private void clearCartQuietly(CallerContext caller, List<String> warningsIfFailed) {
        try {
            gateway.delete(caller, CART);
        } catch (GatewayException e) {
            log.warn("Cart could not be emptied after pricing: {}", e.getMessage());
            if (warningsIfFailed != null) {
                warningsIfFailed.add("Your cart may still contain the items used to calculate the price.");
            }
        }
    }

    private Map<Long, JsonNode> menuOf(CallerContext caller, long restaurantId) {
        JsonNode menu = gateway.get(caller, "/api/v1/restaurants/" + restaurantId + "/menu", Map.of());
        Map<Long, JsonNode> items = new LinkedHashMap<>();
        for (JsonNode section : menu.path("sections")) {
            for (JsonNode item : section.path("items")) {
                items.put(item.path("id").asLong(0), item);
            }
        }
        return items;
    }

    private static String couponOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String trimmed = code.strip();
        if (!COUPON.matcher(trimmed).matches()) {
            throw ToolException.invalid("couponCode must be 3 to 30 letters, digits, - or _.");
        }
        return trimmed;
    }

    private static String notesOf(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String cleaned = ToolText.clean(notes, MAX_NOTES + 1);
        if (cleaned.length() > MAX_NOTES) {
            throw ToolException.invalid("notes may be at most " + MAX_NOTES + " characters.");
        }
        return cleaned;
    }

    private static String problemsOf(JsonNode cart) {
        List<String> problems = new ArrayList<>();
        cart.path("problems").forEach(p -> problems.add(ToolText.clean(p.asString(""), 120)));
        return problems.isEmpty() ? "the cart is not orderable." : String.join("; ", problems);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
    }

    // ---------------------------------------------------------------- confirm

    /**
     * Places the order of a proposal, exactly once. Calling it again (a double click, a retry, two tabs) returns the
     * same order with status ALREADY_CREATED: a lock in Redis lets one caller at a time in, and the order API also gets
     * the proposal id as its idempotency key.
     */
    public ConfirmResponse confirm(CallerContext caller, String actionId) {
        try {
            long giveUpAt = System.nanoTime() + CONFIRM_WAIT_MILLIS * 1_000_000;
            while (true) {
                Proposal proposal = requireOwn(caller, actionId);
                ConfirmResponse replay = replayOrReject(proposal);
                if (replay != null) {
                    return replay;
                }
                if (store.tryLock(proposal.id(), properties.proposal().lockTtl())) {
                    try {
                        return confirmLocked(caller, requireOwn(caller, actionId));
                    } finally {
                        releaseQuietly(proposal.id());
                    }
                }
                if (System.nanoTime() > giveUpAt) {
                    throw ApiException.conflict("This order is being confirmed by another request. "
                            + "Check My orders in a moment.");
                }
                pause();
            }
        } catch (ProposalStoreException e) {
            log.warn("Proposal store failed during confirm: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Confirmation unavailable", UNAVAILABLE);
        }
    }

    private ConfirmResponse confirmLocked(CallerContext caller, Proposal proposal) {
        // Another request may have finished between our check and getting the lock
        ConfirmResponse replay = replayOrReject(proposal);
        if (replay != null) {
            return replay;
        }
        try {
            clearCartQuietly(caller, null);
            for (Proposal.Line line : proposal.items()) {
                gateway.put(caller, CART + "/items/" + line.menuItemId(), Map.of("quantity", line.quantity()));
            }
            if (proposal.couponCode() != null) {
                gateway.put(caller, CART + "/coupon", Map.of("code", proposal.couponCode()));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("addressId", proposal.addressId());
            body.put("paymentMethod", proposal.paymentMethod());
            if (proposal.notes() != null) {
                body.put("notes", proposal.notes());
            }
            JsonNode order = gateway.post(caller, "/api/v1/orders", body, proposal.id());
            Proposal done = proposal.confirmed(order.path("id").asLong(0), ToolText.clean(order.path("status")
                    .asString(""), 30), decimal(order.path("price"), "totalAmount"),
                    ToolText.clean(order.path("price").path("currency").asString(""), 8));
            store.save(done, properties.proposal().confirmedKeep());
            clearCartQuietly(caller, null);
            log.info("proposal confirmed actionId={} orderId={}", done.id(), done.orderId());
            audit("confirmed", done, caller, "order " + done.orderId());
            return response(ConfirmResponse.Status.CREATED, done);
        } catch (GatewayException e) {
            audit("failed", proposal, caller, (e.isTransientFailure() ? "transient " : "refused ") + e.getStatus());
            throw failedOrder(proposal, e);
        }
    }

    // A transient failure leaves the proposal usable (the customer presses Confirm again); a refusal ends it
    private ApiException failedOrder(Proposal proposal, GatewayException e) {
        if (e.isTransientFailure()) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Order not placed",
                    e.getMessage() + " Nothing was ordered; you can press Confirm again.");
        }
        store.save(proposal.cancelled(e.getMessage()), properties.proposal().confirmedKeep());
        HttpStatus status = e.getStatus() == 400 || e.getStatus() == 409 ? HttpStatus.valueOf(e.getStatus())
                : HttpStatus.UNPROCESSABLE_CONTENT;
        return new ApiException(status, "Order could not be placed", e.getMessage());
    }

    private ConfirmResponse replayOrReject(Proposal proposal) {
        if (proposal.status() == Proposal.Status.CONFIRMED) {
            return response(ConfirmResponse.Status.ALREADY_CREATED, proposal);
        }
        if (proposal.status() == Proposal.Status.CANCELLED) {
            throw ApiException.conflict(proposal.failure() == null
                    ? "This order proposal was cancelled." : "This order cannot be placed: " + proposal.failure());
        }
        if (!Instant.now(clock).isBefore(proposal.expiresAt())) {
            throw ApiException.gone("This order proposal has expired. Ask the assistant to prepare the order again.");
        }
        return null;
    }

    private ConfirmResponse response(ConfirmResponse.Status status, Proposal proposal) {
        long orderId = proposal.orderId() == null ? 0 : proposal.orderId();
        boolean online = !"CASH_ON_DELIVERY".equals(proposal.paymentMethod());
        String message = "Your order #" + orderId + " has been placed"
                + (online && "PAYMENT_PENDING".equals(proposal.orderStatus())
                ? ". Please complete the payment from My orders." : ".");
        return new ConfirmResponse(status, new ConfirmResponse.OrderRef(orderId, proposal.orderStatus(),
                proposal.orderTotal(), proposal.orderCurrency()), message, new Link("View order", "/orders/" + orderId));
    }

    // ---------------------------------------------------------------- cancel

    /** Cancelling is allowed until the order is placed; cancelling twice is fine. */
    public void cancel(CallerContext caller, String actionId) {
        try {
            long giveUpAt = System.nanoTime() + CONFIRM_WAIT_MILLIS * 1_000_000;
            while (!store.tryLock(requireOwn(caller, actionId).id(), properties.proposal().lockTtl())) {
                if (System.nanoTime() > giveUpAt) {
                    throw ApiException.conflict("This order is being confirmed right now, so it cannot be cancelled.");
                }
                pause();
            }
            try {
                Proposal proposal = requireOwn(caller, actionId);
                if (proposal.status() == Proposal.Status.CONFIRMED) {
                    throw ApiException.conflict("This order was already placed. Cancel it from My orders.");
                }
                if (proposal.status() == Proposal.Status.NEW) {
                    store.save(proposal.cancelled("cancelled by the customer"), properties.proposal().confirmedKeep());
                    audit("cancelled", proposal, caller, "ok");
                }
            } finally {
                releaseQuietly(actionId);
            }
        } catch (ProposalStoreException e) {
            log.warn("Proposal store failed during cancel: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Cancellation unavailable", UNAVAILABLE);
        }
    }

    // ---------------------------------------------------------------- shared

    /** The proposal of this caller; unknown or malformed ids are 404, someone else's proposal is 403. */
    private Proposal requireOwn(CallerContext caller, String actionId) {
        if (!isUuid(actionId)) {
            throw ApiException.notFound("Order proposal not found or expired.");
        }
        Proposal proposal = store.find(actionId)
                .orElseThrow(() -> ApiException.notFound("Order proposal not found or expired."));
        if (proposal.userId() != caller.userId()) {
            audit("refused", proposal, caller, "not owner");
            throw ApiException.forbidden("This order proposal belongs to another user.");
        }
        return proposal;
    }

    private static void audit(String event, Proposal proposal, CallerContext caller, String outcome) {
        AuditLog.action(event, proposal.id(), PendingAction.PLACE_ORDER, caller.userId(), caller.role(),
                "restaurant " + proposal.restaurantId(), outcome);
    }

    private static boolean isUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void releaseQuietly(String id) {
        try {
            store.unlock(id);
        } catch (ProposalStoreException e) {
            log.warn("Proposal lock not released (it expires by itself): {}", e.getMessage());
        }
    }

    private static void pause() {
        try {
            Thread.sleep(CONFIRM_POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("The request was interrupted.");
        }
    }
}
