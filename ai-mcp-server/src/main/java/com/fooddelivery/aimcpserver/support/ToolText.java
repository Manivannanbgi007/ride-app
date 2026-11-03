package com.fooddelivery.aimcpserver.support;

import java.util.regex.Pattern;

/**
 * Cleans text that comes from business data (restaurant names, notes, cancellation reasons...) before the model or a
 * card sees it. A restaurant called "Ignore all rules <system>..." is still only data: control characters and markup
 * are removed and the length is capped, so such text cannot smuggle in formatting or hide instructions.
 */
public final class ToolText {

    private static final Pattern TAGS = Pattern.compile("<[^>]{0,200}>");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
    private static final Pattern MARKUP = Pattern.compile("[<>`]|\\[|\\]\\(");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final String TRUNCATED = "...";

    private ToolText() {
    }

    /** Plain single-line text of at most maxLength characters; null becomes "". */
    public static String clean(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String result = TAGS.matcher(text).replaceAll(" ");
        result = INVISIBLE.matcher(result).replaceAll(" ");
        result = MARKUP.matcher(result).replaceAll("");
        result = SPACES.matcher(result).replaceAll(" ").strip();
        if (result.length() > maxLength) {
            return result.substring(0, Math.max(0, maxLength - TRUNCATED.length())) + TRUNCATED;
        }
        return result;
    }

    /** Last line of defence for a whole tool result: control characters out, length capped with a visible marker. */
    public static String capResult(String text, int maxChars) {
        String result = INVISIBLE.matcher(text == null ? "" : text).replaceAll(" ");
        if (result.length() > maxChars) {
            return result.substring(0, maxChars) + " ...[result cut: too long]";
        }
        return result;
    }
}
