package com.fooddelivery.restaurantservice.dto.response;

import java.util.List;

/** Customer-facing restaurant page. No business documents, owner or status data. */
public record PublicRestaurantDetailsResponse(
        Long id,
        String name,
        String description,
        List<String> cuisines,
        String phone,
        AddressResponse address,
        String timeZone,
        List<OperatingHoursResponse> operatingHours,
        boolean openNow
) {

    /** The same page with "open now" worked out for this moment (it is not cached). */
    public PublicRestaurantDetailsResponse withOpenNow(boolean open) {
        return new PublicRestaurantDetailsResponse(id, name, description, cuisines, phone, address, timeZone,
                operatingHours, open);
    }
}
