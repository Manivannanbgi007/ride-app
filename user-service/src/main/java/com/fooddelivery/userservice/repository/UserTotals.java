package com.fooddelivery.userservice.repository;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;

/** Number of users per role (and status), counted in the database. */
public interface UserTotals {

    Role getRole();

    UserStatus getStatus();

    long getTotal();
}
