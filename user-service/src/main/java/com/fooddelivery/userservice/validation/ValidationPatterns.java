package com.fooddelivery.userservice.validation;

public final class ValidationPatterns {

    /** 10 to 15 digits, optionally starting with "+" (for example +919876543210). */
    public static final String PHONE = "^\\+?[0-9]{10,15}$";

    public static final String PHONE_MESSAGE = "must be 10-15 digits, optionally starting with +";

    /** Letters, digits, spaces and hyphens, 3 to 10 characters (covers PIN codes and most postal codes). */
    public static final String POSTAL_CODE = "^[A-Za-z0-9 -]{3,10}$";

    private ValidationPatterns() {
    }
}
