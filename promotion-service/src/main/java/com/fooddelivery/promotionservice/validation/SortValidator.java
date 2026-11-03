package com.fooddelivery.promotionservice.validation;

import java.util.List;

import com.fooddelivery.promotionservice.exception.InvalidRequestException;
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
}
