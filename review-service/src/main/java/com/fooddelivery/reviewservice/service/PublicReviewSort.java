package com.fooddelivery.reviewservice.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.fooddelivery.reviewservice.validation.SortValidator;
import org.springframework.data.domain.Sort;

/** The orders of the public review list. The id is the last key, so no review repeats or is skipped between pages. */
public enum PublicReviewSort {

    NEWEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    HIGHEST(Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    LOWEST(Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    private static final List<String> NAMES = Arrays.stream(values()).map(sort -> sort.name().toLowerCase(Locale.ROOT)).toList();

    private final Sort sort;

    PublicReviewSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }

    /** "newest", "highest" or "lowest"; anything else is a 400. */
    public static PublicReviewSort parse(String name) {
        SortValidator.validateName(name, NAMES);
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
