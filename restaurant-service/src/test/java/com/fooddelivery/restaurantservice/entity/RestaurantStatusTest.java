package com.fooddelivery.restaurantservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RestaurantStatusTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PENDING, APPROVED",
            "PENDING, REJECTED",
            "REJECTED, PENDING",
            "APPROVED, ACTIVE",
            "ACTIVE, INACTIVE",
            "ACTIVE, SUSPENDED",
            "INACTIVE, ACTIVE",
            "INACTIVE, SUSPENDED",
            "SUSPENDED, ACTIVE"
    })
    void allowedTransitions(RestaurantStatus from, RestaurantStatus to) {
        assertThat(from.canChangeTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is not allowed")
    @CsvSource({
            "PENDING, ACTIVE",       // must be approved first
            "REJECTED, APPROVED",    // must be resubmitted first
            "REJECTED, ACTIVE",
            "APPROVED, SUSPENDED",
            "SUSPENDED, INACTIVE",
            "ACTIVE, PENDING",
            "ACTIVE, ACTIVE"
    })
    void forbiddenTransitions(RestaurantStatus from, RestaurantStatus to) {
        assertThat(from.canChangeTo(to)).isFalse();
    }
}
