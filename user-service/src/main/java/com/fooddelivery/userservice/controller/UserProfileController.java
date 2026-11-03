package com.fooddelivery.userservice.controller;

import com.fooddelivery.userservice.dto.request.ChangePasswordRequest;
import com.fooddelivery.userservice.dto.request.UpdateProfileRequest;
import com.fooddelivery.userservice.dto.response.UserResponse;
import com.fooddelivery.userservice.service.UserProfileService;
import com.fooddelivery.userservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My profile", description = "The calling user's own account")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Get my profile")
    @GetMapping
    public UserResponse getMyProfile(CallerInfo caller) {
        return userProfileService.getProfile(caller.userId());
    }

    @Operation(summary = "Update my name and phone")
    @PutMapping
    public UserResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request, CallerInfo caller) {
        return userProfileService.updateProfile(caller.userId(), request);
    }

    @Operation(summary = "Change my password")
    @PutMapping("/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request, CallerInfo caller) {
        userProfileService.changePassword(caller.userId(), request);
        return ResponseEntity.noContent().build();
    }
}
