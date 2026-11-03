package com.fooddelivery.userservice.exception;

public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException(String email) {
        super("Email is already registered: " + email);
    }
}
