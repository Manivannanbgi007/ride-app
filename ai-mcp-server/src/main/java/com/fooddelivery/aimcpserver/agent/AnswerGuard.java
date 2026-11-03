package com.fooddelivery.aimcpserver.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches answers a small model made up. When the model called no tool in a turn, every figure or code in its answer
 * (counts, prices, dates, ids, coupon codes with digits) must already be in the user's words, the conversation or
 * today's date. Anything else came from the model's memory: it is treated as invented. This is code, not a prompt rule,
 * so the model cannot talk its way around it.
 */
final class AnswerGuard {

    // A number (with , . : / - inside) or a word that contains a digit, such as BIRYANI10
    private static final Pattern FIGURE = Pattern.compile("[A-Za-z]*\\d[A-Za-z0-9]*(?:[.,:/-]\\d[A-Za-z0-9]*)*");
    // "1. First item": numbering of a list is not a figure
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*(?:[-*]\\s*)?\\d{1,2}[.)]\\s+");

    private AnswerGuard() {
    }

    static List<String> figures(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = FIGURE.matcher(LIST_MARKER.matcher(text == null ? "" : text).replaceAll(""));
        while (matcher.find()) {
            found.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return found;
    }

    /** True when the answer contains a figure or code that none of the known texts contains. */
    static boolean isUngrounded(String answer, List<String> knownTexts) {
        String known = String.join("\n", knownTexts).toLowerCase(Locale.ROOT);
        return figures(answer).stream().anyMatch(figure -> !known.contains(figure));
    }

    /** The note that goes back to the model for the one retry. */
    static String directive(List<String> toolNames) {
        return "System note: your last answer contained figures, names or codes that did not come from the user or from "
                + "a tool. You must call a tool to get this information; do not answer from memory. "
                + "Available tools: " + String.join(", ", toolNames) + ". Call the right tool now, then answer only from "
                + "its result.";
    }
}
