package com.fooddelivery.reviewservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.reviewservice.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PublicReviewSortTest {

    @Test
    void theThreeNamesMapToTheirOrders() {
        assertThat(PublicReviewSort.parse("newest").sort()).isEqualTo(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(PublicReviewSort.parse("highest").sort()).isEqualTo(
                Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(PublicReviewSort.parse("lowest").sort()).isEqualTo(
                Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void anythingElseIsRefusedWithTheAllowedValues() {
        for (String name : new String[] {"best", "NEWEST", "createdAt", "", "rating,desc"}) {
            assertThatThrownBy(() -> PublicReviewSort.parse(name))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Cannot sort by '" + name + "'. Allowed values: [newest, highest, lowest]");
        }
    }
}
