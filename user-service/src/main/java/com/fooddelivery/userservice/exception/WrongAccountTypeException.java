package com.fooddelivery.userservice.exception;

/**
 * Correct email and password, but the account belongs to another area (for example a customer signing in on the
 * restaurant owner page). Mapped to HTTP 403 with {@code code = WRONG_ACCOUNT_TYPE}, so the web page can explain it.
 */
public class WrongAccountTypeException extends ForbiddenException {

    public static final String CODE = "WRONG_ACCOUNT_TYPE";

    public WrongAccountTypeException() {
        super("This account cannot sign in here.");
    }
}
