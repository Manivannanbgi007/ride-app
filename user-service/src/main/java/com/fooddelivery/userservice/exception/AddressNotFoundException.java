package com.fooddelivery.userservice.exception;

public class AddressNotFoundException extends ResourceNotFoundException {

    public AddressNotFoundException(Long addressId) {
        super("Address not found: " + addressId);
    }
}
