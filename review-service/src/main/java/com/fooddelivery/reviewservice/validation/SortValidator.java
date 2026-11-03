package com.fooddelivery.reviewservice.validation;

import java.util.List;

import com.fooddelivery.reviewservice.exception.InvalidRequestException;
import org.springframework.data.domain.Sort;

/** Only allow sorting by known fields: unknown fields would fail deep inside JPA with a 500. */
public final class SortValidator {

    private SortValidator() {
    }

    public static void validate(Sort sort, List<String> allowedFields) {
        for (Sort.Order order : sort) {
            if (!allowedFields.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Cannot sort by '" + order.getProperty() + "'. Allowed fields: " + allowedFields);
            }
        }
    }

    /** For a sort given as a name (for example {@code sort=newest}) instead of a field: only the listed names. */
    public static void validateName(String name, List<String> allowedNames) {
        if (!allowedNames.contains(name)) {
            throw new InvalidRequestException("Cannot sort by '" + name + "'. Allowed values: " + allowedNames);
        }
    }
}
