package com.fooddelivery.userservice.dto.response;

import java.time.LocalDate;
import java.util.Map;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;

/**
 * User numbers for the admin dashboard and the customer report.
 *
 * @param usersNow      all accounts right now, per role and status
 * @param customers     customer accounts right now (active + inactive)
 * @param newCustomers  customers who signed up in the period
 * @param newUsersByRole sign-ups in the period, per role
 */
public record UserStatsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Map<Role, Map<UserStatus, Long>> usersNow,
        long customers,
        long activeCustomers,
        long inactiveCustomers,
        long newCustomers,
        Map<Role, Long> newUsersByRole
) {
}
