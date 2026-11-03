package com.fooddelivery.userservice.service;

import java.util.Locale;

/** Normalizes user input so that "Priya@Example.com " and "priya@example.com" are the same account. */
public final class UserInputNormalizer {

    private UserInputNormalizer() {
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns null for a missing or blank phone number. */
    public static String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
