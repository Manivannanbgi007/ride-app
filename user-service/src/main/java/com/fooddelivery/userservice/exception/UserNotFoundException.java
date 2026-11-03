package com.fooddelivery.userservice.exception;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Long userId) {
        super("User not found: " + userId);
    }
}
