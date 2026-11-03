package com.fooddelivery.restaurantservice.repository;

/** Number of restaurants submitted on one business day ("yyyy-MM-dd"), counted in the database. */
public interface RegistrationDayTotal {

    String getDay();

    long getTotal();
}
