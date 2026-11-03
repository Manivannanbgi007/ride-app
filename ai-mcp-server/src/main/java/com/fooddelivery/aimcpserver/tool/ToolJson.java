package com.fooddelivery.aimcpserver.tool;

import java.math.BigDecimal;
import java.util.List;

import com.fooddelivery.aimcpserver.support.ToolText;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Small helpers to read API responses (fields that may be missing) and to build compact tool summaries. */
public final class ToolJson {

    private static final int DEFAULT_TEXT = 120;

    private ToolJson() {
    }

    /** Cleaned text of a field, "" when it is missing. */
    public static String text(JsonNode node, String field) {
        return ToolText.clean(node.path(field).asString(""), DEFAULT_TEXT);
    }

    /** An absolute https URL as it is (not shortened), or "" when missing or not https. */
    public static String url(JsonNode node, String field) {
        String value = node.path(field).asString("").strip();
        return value.startsWith("https://") && value.length() <= 500 && value.chars().noneMatch(Character::isISOControl)
                ? value : "";
    }

    public static long number(JsonNode node, String field) {
        return node.path(field).asLong(0);
    }

    /** The amount of a field, or null when it is missing or not a number. */
    public static BigDecimal amount(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    public static String amountText(JsonNode node, String field) {
        BigDecimal value = amount(node, field);
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * Puts as many items as fit into the character budget under "field" and adds "more" with how many were left out,
     * so a long list can never push the result past the cap (and the model knows the list is incomplete).
     */
    public static void putCapped(ObjectNode root, String field, List<ObjectNode> items, int budgetChars) {
        ArrayNode array = root.putArray(field);
        int used = root.toString().length();
        int shown = 0;
        for (ObjectNode item : items) {
            used += item.toString().length() + 1;
            if (used > budgetChars) {
                break;
            }
            array.add(item);
            shown++;
        }
        if (shown < items.size()) {
            root.put("more", items.size() - shown);
            root.put("note", (items.size() - shown) + " more not shown");
        }
    }
}
