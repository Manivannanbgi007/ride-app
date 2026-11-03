package com.fooddelivery.restaurantservice.dto.response;

/**
 * The restaurant owner, as reported by user-service.
 * If user-service cannot be reached, lookupStatus is UNAVAILABLE and the other fields are null:
 * the admin screen still works, it just shows less owner information.
 */
public record OwnerResponse(
        Long userId,
        LookupStatus lookupStatus,
        String fullName,
        String email,
        String phone,
        String accountStatus
) {

    public enum LookupStatus {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE
    }
}
