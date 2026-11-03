package com.fooddelivery.searchservice.dto.response;

import java.util.List;

/**
 * Autocomplete for a search box: a few restaurants, dishes and categories whose names start with what was typed.
 *
 * @param query what was typed
 */
public record Suggestions(String query, List<Suggestion> restaurants, List<Suggestion> foods,
                          List<Suggestion> categories) {

    /** @param detail a hint shown next to the name: the city of a restaurant, the restaurant of a dish */
    public record Suggestion(Long id, String name, String detail) {
    }
}
