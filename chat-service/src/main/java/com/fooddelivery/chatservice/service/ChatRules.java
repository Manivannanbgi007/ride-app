package com.fooddelivery.chatservice.service;

import java.util.Locale;

import com.fooddelivery.chatservice.client.UserInfo;
import com.fooddelivery.chatservice.exception.BusinessRuleException;
import com.fooddelivery.chatservice.exception.TooManyRequestsException;

/** The rules of the chat as plain static methods without database or network: easy to read and to unit test. */
public final class ChatRules {

    /** The longest preview the conversation stores (the inbox shows it as a one-line snippet). */
    public static final int MAX_PREVIEW_LENGTH = 200;

    /** The longest name the database stores (user names are copied into conversations and messages). */
    public static final int MAX_NAME_LENGTH = 120;

    /** Escape character of the LIKE patterns built here (the repository queries name it too). */
    private static final char ESCAPE = '!';

    private static final String ELLIPSIS = "…";

    private ChatRules() {
    }

    /** One line of the message for the inbox: white space collapsed, cut with an ellipsis when too long. */
    public static String preview(String body) {
        String oneLine = body.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= MAX_PREVIEW_LENGTH
                ? oneLine
                : oneLine.substring(0, MAX_PREVIEW_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    /**
     * A sender who already wrote {@code maxMessages} messages in the current window may not write another one.
     * It is a plain count of saved messages, so two requests at the very same moment may both pass: this stops a
     * runaway client, it is not an exact meter.
     */
    public static void checkRateLimit(long sentInWindow, int maxMessages) {
        if (sentInWindow >= maxMessages) {
            throw new TooManyRequestsException("Too many messages: at most " + maxMessages
                    + " per sender in a short time. Wait a moment and try again.");
        }
    }

    /** Admins answer the chats of other users; they have no conversation of their own. */
    public static void checkCanBeParticipant(UserInfo user) {
        if (user.isPlatformAdmin()) {
            throw new BusinessRuleException("Platform admins cannot be chat participants: user " + user.id());
        }
    }

    /** Cuts a copied name to what the database column holds, so a very long name cannot make saving fail. */
    public static String fitName(String name) {
        if (name == null || name.isBlank()) {
            return "Unknown";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    /**
     * The LIKE pattern for a "contains" search, in lower case, with the '%' and '_' of the text made literal.
     * No text means "%", which matches everything.
     */
    public static String containsPattern(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        StringBuilder pattern = new StringBuilder("%");
        for (char c : text.strip().toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE) {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }
}
