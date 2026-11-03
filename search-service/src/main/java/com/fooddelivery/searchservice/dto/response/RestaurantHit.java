package com.fooddelivery.searchservice.dto.response;

import java.util.List;

/**
 * @param distanceKm    from the position sent with the search (null without one, or for a restaurant without coordinates)
 * @param ratingAverage the customers' average rating, 1.0 to 5.0 (null while the restaurant has no rating)
 * @param ratingCount   how many ratings that average is made of (0 while the restaurant has none)
 */
public record RestaurantHit(
        Long id,
        String name,
        String description,
        List<String> cuisines,
        String city,
        String addressLine,
        Double distanceKm,
        Double ratingAverage,
        int ratingCount
) {
}
