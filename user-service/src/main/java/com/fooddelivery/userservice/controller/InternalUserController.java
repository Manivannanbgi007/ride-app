package com.fooddelivery.userservice.controller;

import com.fooddelivery.userservice.dto.response.InternalAddressResponse;
import com.fooddelivery.userservice.dto.response.InternalUserResponse;
import com.fooddelivery.userservice.service.AddressService;
import com.fooddelivery.userservice.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. The API Gateway has no route for /api/v1/internal/**,
 * so these endpoints are reachable only from inside the platform network.
 * (Service-to-service authentication is added in the security phase.)
 */
@Tag(name = "Internal", description = "For other microservices only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserProfileService userProfileService;
    private final AddressService addressService;

    @Operation(summary = "Basic user data by id")
    @GetMapping("/{userId}")
    public InternalUserResponse getUser(@PathVariable Long userId) {
        return userProfileService.getInternalUser(userId);
    }

    @Operation(summary = "One address of a user (404 if the address belongs to another user)")
    @GetMapping("/{userId}/addresses/{addressId}")
    public InternalAddressResponse getAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        return addressService.getInternalAddress(userId, addressId);
    }
}
