package com.fooddelivery.aimcpserver.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpserver.security.Role;

/**
 * Catches a small model that answers "Shall I accept order 25?" instead of preparing the change the user asked for, or
 * that says a prepared change "is now done".
 * <p>
 * Preparing never changes anything (the user still has to press the Confirm button), so asking first only costs the user
 * another message. When the user's own message is clearly a change request for their role, this class names the prepare
 * tool and reads the details from the message (order number, step, reason, dish). The agent then retries once with a
 * note naming the tool and the details, and if the model still prepares nothing, calls the tool itself with them.
 * This is code, not a prompt rule: it does not depend on the model following instructions it already ignored once.
 * <p>
 * Only the user's message decides (never a tool result), and questions about the past or reports ("how many orders did I
 * accept?", "which dishes are sold out?") are not change requests.
 */
final class ChangeRequestGuard {

    /**
     * The change the user's message asks for.
     *
     * @param tool      the prepare tool
     * @param arguments what the message says, in the tool's argument names
     * @param complete  true when the arguments are enough to call the tool without the model
     */
    record ExpectedChange(String tool, Map<String, Object> arguments, boolean complete) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final int MAX_REASON = 300;
    private static final int MAX_DISH = 100;

    // A message that starts like a question or a report request asks for information, not for a change
    private static final Pattern INFORMATION_REQUEST = Pattern.compile(
            "^\\W*(is|are|was|were|did|does|do|has|have|had|how|what|what's|when|where|which|who|why|show|list|"
                    + "tell me|give me|find|search)\\b", FLAGS);
    // "order 42", "#42", or else the first number
    private static final Pattern ORDER_NUMBER = Pattern.compile("(?:order\\s*#?|#)\\s*(\\d+)|\\b(\\d+)\\b", FLAGS);
    // The words after the number that explain why: "reject 42, we ran out of chicken", "... 42 because ..."
    private static final Pattern REASON = Pattern.compile(
            "\\d+\\s*(?:[,;:\\-]|\\b(?:because|since|as|due\\s+to)\\b)\\s*(.+)$", FLAGS);
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.!?,;:]+$");

    private static final Pattern REJECT = Pattern.compile("\\b(reject|decline|refuse)\\b", FLAGS);
    private static final Pattern ACCEPT = Pattern.compile("\\baccept\\b", FLAGS);
    private static final Pattern START_PREPARING = Pattern.compile(
            "\\bstart(ing)?\\s+(preparing|to\\s+prepare|cooking)\\b", FLAGS);
    private static final Pattern READY = Pattern.compile("\\bready\\b", FLAGS);

    private static final String SOLD_OUT = "sold\\s*out|out\\s+of\\s+stock|unavailable|not\\s+available";
    private static final String AVAILABLE_AGAIN = "available\\s+again|back\\s+in\\s+stock|in\\s+stock\\s+again";
    private static final Pattern OWNER_AVAILABILITY = Pattern.compile(
            "\\b(" + SOLD_OUT + "|r[au]n\\s+out\\s+of|" + AVAILABLE_AGAIN + ")\\b", FLAGS);
    private static final Pattern AVAILABLE_AGAIN_WORDS = Pattern.compile("\\b(" + AVAILABLE_AGAIN + ")\\b", FLAGS);
    // Where the dish name sits: "mark X as sold out", "X is sold out", "we ran out of X", "X sold out"
    private static final Pattern[] DISH = {
        Pattern.compile("\\b(?:mark|set|make)\\s+(?:the\\s+)?(.+?)\\s+(?:as\\s+)?(?:" + SOLD_OUT + "|"
                + AVAILABLE_AGAIN + "|available)\\b", FLAGS),
        Pattern.compile("^\\W*(?:please\\s+)?(?:the\\s+)?(.+?)\\s+(?:is|are)\\s+(?:now\\s+)?(?:" + SOLD_OUT + "|"
                + AVAILABLE_AGAIN + ")\\b", FLAGS),
        Pattern.compile("\\br[au]n\\s+out\\s+of\\s+(?:the\\s+)?(.+?)\\s*$", FLAGS),
        Pattern.compile("^\\W*(?:the\\s+)?(.+?)\\s+(?:" + SOLD_OUT + ")\\b", FLAGS)
    };

    private static final Pattern PARTNER_STEP = Pattern.compile(
            "\\b(accept|decline|reject|delivered|collected)\\b|\\bpick(ed)?\\s*(it\\s+)?up\\b|\\bhanded\\s+(it\\s+)?over\\b",
            FLAGS);
    private static final Pattern PARTNER_DECLINE = Pattern.compile("\\b(decline|reject)\\b", FLAGS);
    private static final Pattern PARTNER_DELIVERED = Pattern.compile("\\b(delivered|handed\\s+(it\\s+)?over)\\b", FLAGS);
    private static final Pattern PARTNER_PICKED_UP = Pattern.compile(
            "\\bpick(ed)?\\s*(it\\s+)?up\\b|\\bcollected\\b", FLAGS);

    private static final Pattern CANCEL = Pattern.compile("\\bcancel\\b", FLAGS);
    private static final Pattern REASSIGN = Pattern.compile(
            "\\breassign\\b|\\b(give|assign|move)\\b.*\\bto\\b|\\bback\\s+(in|to)\\s+the\\s+queue\\b", FLAGS);

    // A prepared change described as already done ("Order #43 is now marked as ready")
    private static final Pattern CLAIMS_DONE = Pattern.compile(
            "\\b(is now|are now|has been|have been|now marked|marked as|successfully)\\b"
                    + "|\\bI(?:'ve| have) (?:accepted|rejected|marked|cancell?ed|updated|reassigned|delivered|picked)\\b",
            FLAGS);

    private ChangeRequestGuard() {
    }

    /** The prepare tool the user's message asks for, if it is clearly a change request for this role. */
    static Optional<String> expectedTool(Role role, String message) {
        return expectedChange(role, message).map(ExpectedChange::tool);
    }

    /** The change the user's message asks for, with the details it gives, if it is clearly a change request. */
    static Optional<ExpectedChange> expectedChange(Role role, String message) {
        if (message == null || message.isBlank() || INFORMATION_REQUEST.matcher(message).find()) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (role) {
            case RESTAURANT_ADMIN -> ownerChange(message);
            case DELIVERY_PARTNER -> partnerChange(message);
            case CUSTOMER -> customerChange(message);
            case PLATFORM_ADMIN -> adminChange(message);
        });
    }

    /** The note that goes back to the model for the one retry. */
    static String directive(ExpectedChange change) {
        String details = change.arguments().isEmpty() ? "with the details from their message"
                : "with these details from their message: " + change.arguments();
        return "System note: in their last message the user asked for a change, and you answered without preparing it. "
                + "Do not ask whether they want it and do not ask them to confirm: call " + change.tool() + " now "
                + details + ". It changes nothing by itself: it shows them a Confirm button, and that button is the "
                + "confirmation. If something is missing, the tool tells you what to ask.";
    }

    /** True when an answer describes a change as done, which it never is before the user presses the button. */
    static boolean claimsDone(String answer) {
        return answer != null && CLAIMS_DONE.matcher(answer).find();
    }

    private static ExpectedChange ownerChange(String message) {
        Long orderId = orderNumber(message);
        String step = orderId == null ? null : ownerStep(message);
        if (step != null) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("orderId", orderId);
            arguments.put("step", step);
            String reason = "REJECT".equals(step) ? reason(message) : null;
            if (reason != null) {
                arguments.put("reason", reason);
            }
            // A rejection needs the customer-facing reason: without one the model asks for it
            return new ExpectedChange("owner_prepare_order_step", arguments, !"REJECT".equals(step) || reason != null);
        }
        if (!OWNER_AVAILABILITY.matcher(message).find()) {
            return null;
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        String dish = dishName(message);
        if (dish != null) {
            arguments.put("dish", dish);
        }
        arguments.put("available", AVAILABLE_AGAIN_WORDS.matcher(message).find());
        return new ExpectedChange("owner_prepare_item_availability", arguments, dish != null);
    }

    private static String ownerStep(String message) {
        if (REJECT.matcher(message).find()) {
            return "REJECT";
        }
        if (ACCEPT.matcher(message).find()) {
            return "ACCEPT";
        }
        if (START_PREPARING.matcher(message).find()) {
            return "START_PREPARING";
        }
        return READY.matcher(message).find() ? "MARK_READY" : null;
    }

    private static ExpectedChange partnerChange(String message) {
        if (!PARTNER_STEP.matcher(message).find()) {
            return null;
        }
        String step;
        if (PARTNER_DECLINE.matcher(message).find()) {
            step = "DECLINE";
        } else if (PARTNER_DELIVERED.matcher(message).find()) {
            step = "DELIVERED";
        } else if (PARTNER_PICKED_UP.matcher(message).find()) {
            step = "PICKED_UP";
        } else {
            step = "ACCEPT";
        }
        // Without a delivery id the tool uses the partner's current delivery
        return new ExpectedChange("partner_prepare_delivery_step", Map.of("step", step), true);
    }

    private static ExpectedChange customerChange(String message) {
        if (!CANCEL.matcher(message).find()) {
            return null;
        }
        Long orderId = orderNumber(message);
        // Without a number the model finds the order with my_orders first
        return orderId == null
                ? new ExpectedChange("prepare_cancel_order", Map.of(), false)
                : new ExpectedChange("prepare_cancel_order", Map.of("orderId", orderId), true);
    }

    // Admin interventions need a reason for the audit log and a careful choice: the model prepares them, never the code
    private static ExpectedChange adminChange(String message) {
        Long number = orderNumber(message);
        if (number == null) {
            return null;
        }
        if (REASSIGN.matcher(message).find()) {
            return new ExpectedChange("admin_prepare_reassign_delivery", Map.of("deliveryId", number), false);
        }
        return CANCEL.matcher(message).find()
                ? new ExpectedChange("admin_prepare_cancel_order", Map.of("orderId", number), false) : null;
    }

    private static Long orderNumber(String message) {
        Matcher matcher = ORDER_NUMBER.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        try {
            long value = Long.parseLong(digits);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String reason(String message) {
        Matcher matcher = REASON.matcher(message);
        return matcher.find() ? cleaned(matcher.group(1), MAX_REASON) : null;
    }

    private static String dishName(String message) {
        for (Pattern pattern : DISH) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String dish = cleaned(matcher.group(1), MAX_DISH);
                if (dish != null && dish.length() >= 2) {
                    return dish;
                }
            }
        }
        return null;
    }

    private static String cleaned(String text, int maxLength) {
        String value = TRAILING_PUNCTUATION.matcher(text.strip()).replaceAll("");
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
