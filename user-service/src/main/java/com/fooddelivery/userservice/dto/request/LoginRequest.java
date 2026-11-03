package com.fooddelivery.userservice.dto.request;

import com.fooddelivery.userservice.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param role optional: the area the user signs in to (e.g. RESTAURANT_ADMIN on the owner page). When given, an account
 *             of another role is refused with 403 WRONG_ACCOUNT_TYPE and no session is started.
 */
public record LoginRequest(

        @NotBlank @Email
        String email,

        @NotBlank
        String password,

        Role role
) {

    public LoginRequest(String email, String password) {
        this(email, password, null);
    }

    // Records print every field: never put a password into a log line by accident
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****, role=" + role + "]";
    }
}
