package com.fooddelivery.deliveryservice.dto.response;

import java.time.Instant;

import com.fooddelivery.deliveryservice.entity.VehicleType;

/**
 * The live map of a delivery for its customer: the partner's real GPS position and the two addresses.
 * <p>
 * Only what the platform really knows: no route, no distance and no arrival time (there is no routing service). A place
 * without map coordinates has {@code location} null, and {@code rider} is null until the partner's phone has sent a
 * position (or once the delivery is delivered).
 *
 * @param source always GPS: the position comes from the partner's phone
 * @param phase  TO_RESTAURANT (accepted), TO_CUSTOMER (picked up) or DELIVERED
 */
public record LiveTrackingResponse(
        Long orderId,
        String source,
        String phase,
        Place restaurant,
        Place dropoff,
        Partner partner,
        Rider rider,
        Instant deliveredAt
) {

    public static final String SOURCE_GPS = "GPS";

    /** A point on the map (WGS84 degrees). */
    public record GeoPoint(double lat, double lng) {
    }

    public record Place(String name, String address, GeoPoint location) {
    }

    /** {@code phone} is shared only while the partner is on the way. */
    public record Partner(String name, String phone, VehicleType vehicleType) {
    }

    /**
     * @param updatedAt when delivery-service received the position
     */
    public record Rider(GeoPoint location, Integer headingDeg, Integer accuracyMeters, Instant updatedAt) {
    }
}
