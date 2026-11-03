package com.fooddelivery.restaurantservice.entity;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a short list of strings in one column, for example "North Indian,Chinese".
 * Good enough for a few tags; real searching is done by the Search Service later.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        return values == null ? "" : String.join(SEPARATOR, values);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        // Immutable on purpose: change the list by setting a new one, so JPA notices the change.
        return List.copyOf(Arrays.asList(column.split(SEPARATOR)));
    }
}
