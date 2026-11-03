package com.fooddelivery.reviewservice.mapper;

import java.util.Locale;

/**
 * What a visitor sees instead of the customer's full name: the first name and the initial of the last name
 * ("Priya Sharma" becomes "Priya S."). A name of one word stays as it is.
 */
public final class ReviewerNames {

    /** Shown when the saved name has no letters at all. */
    static final String FALLBACK = "Customer";

    private ReviewerNames() {
    }

    public static String mask(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return FALLBACK;
        }
        String[] words = fullName.trim().split("\s+");
        if (words.length == 1) {
            return words[0];
        }
        String lastWord = words[words.length - 1];
        // A code point, not a char: an initial outside the basic plane must not be cut in half
        String initial = new String(Character.toChars(lastWord.codePointAt(0))).toUpperCase(Locale.ROOT);
        return words[0] + " " + initial + ".";
    }
}
