package com.fooddelivery.reviewservice.validation;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fooddelivery.reviewservice.exception.InvalidRequestException;

/** Reads a query parameter such as {@code ids=1,2,3}: positive numbers, at most {@code max} different ones. */
public final class IdListParser {

    private IdListParser() {
    }

    /** An id that is repeated counts once. Anything else wrong (empty, not a positive number, too many) is a 400. */
    public static Set<Long> parse(String raw, int max) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("ids is required: a comma-separated list of 1 to " + max + " ids");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : raw.split(",", -1)) {
            ids.add(parseId(part.trim()));
            if (ids.size() > max) {
                throw new InvalidRequestException("At most " + max + " ids at a time");
            }
        }
        return ids;
    }

    private static long parseId(String text) {
        try {
            long id = Long.parseLong(text);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Invalid id '" + text + "': ids are positive numbers");
        }
    }
}
