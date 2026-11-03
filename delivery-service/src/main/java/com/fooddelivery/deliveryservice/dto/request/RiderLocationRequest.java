package com.fooddelivery.deliveryservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The partner's position from the phone's GPS (the browser's Geolocation API), in WGS84 degrees.
 *
 * @param accuracyMeters how exact the phone says the position is, if it says so
 * @param headingDeg     direction of travel, degrees clockwise from north, if the phone knows it (only while moving)
 */
public record RiderLocationRequest(

        @NotNull @DecimalMin("-90") @DecimalMax("90")
        Double latitude,

        @NotNull @DecimalMin("-180") @DecimalMax("180")
        Double longitude,

        @Min(0) @Max(100_000)
        Integer accuracyMeters,

        @Min(0) @Max(360)
        Integer headingDeg
) {
}
