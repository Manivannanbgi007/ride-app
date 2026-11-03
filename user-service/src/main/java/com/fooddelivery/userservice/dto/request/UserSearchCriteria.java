package com.fooddelivery.userservice.dto.request;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Size;

/** Optional filters for the admin user list, bound from query parameters. */
public record UserSearchCriteria(

        @Parameter(description = "Matches name, email or phone (contains, case-insensitive)")
        @Size(max = 100)
        String search,

        @Parameter(description = "Filter by role")
        Role role,

        @Parameter(description = "Filter by status")
        UserStatus status
) {
}
