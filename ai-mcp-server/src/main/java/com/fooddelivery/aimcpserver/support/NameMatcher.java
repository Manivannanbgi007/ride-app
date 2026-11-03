package com.fooddelivery.aimcpserver.support;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Finds what the user meant by a name: case-insensitive, accent-insensitive, word based. Used instead of asking for ids. */
public final class NameMatcher {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final int MIN_NAME_IN_QUERY = 3;

    private NameMatcher() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String lower = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        return NON_ALNUM.matcher(lower).replaceAll(" ").strip();
    }

    /**
     * Candidates that fit the query, best fit first: an exact name; else names containing every word of the query;
     * else names that the query contains (the user added extra words).
     */
    public static <T> List<T> match(String query, List<T> candidates, Function<T, String> nameOf) {
        String wanted = normalize(query);
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<T> exact = new ArrayList<>();
        List<T> allWords = new ArrayList<>();
        List<T> inQuery = new ArrayList<>();
        for (T candidate : candidates) {
            String name = normalize(nameOf.apply(candidate));
            if (name.equals(wanted)) {
                exact.add(candidate);
            } else if (containsAllWords(name, wanted)) {
                allWords.add(candidate);
            } else if (name.length() >= MIN_NAME_IN_QUERY && (" " + wanted + " ").contains(" " + name + " ")) {
                inQuery.add(candidate);
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }
        return allWords.isEmpty() ? inQuery : allWords;
    }

    private static boolean containsAllWords(String name, String wanted) {
        for (String word : wanted.split(" ")) {
            if (!name.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
