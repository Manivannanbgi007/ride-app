package com.fooddelivery.deliveryservice.mapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fooddelivery.deliveryservice.dto.response.AddressResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliveryResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliverySummaryResponse;
import com.fooddelivery.deliveryservice.dto.response.LiveTrackingResponse;
import com.fooddelivery.deliveryservice.dto.response.PartnerResponse;
import com.fooddelivery.deliveryservice.dto.response.RosterPartnerResponse;
import com.fooddelivery.deliveryservice.dto.response.TrackingResponse;
import com.fooddelivery.deliveryservice.entity.Address;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryEvent;
import com.fooddelivery.deliveryservice.entity.DeliveryEventType;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.RiderLocation;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMapper {

    /** The milestones a customer sees; offers, declines and reassignments stay internal. */
    private static final Set<DeliveryEventType> CUSTOMER_STEPS = Set.of(DeliveryEventType.CREATED,
            DeliveryEventType.ACCEPTED, DeliveryEventType.PICKED_UP, DeliveryEventType.DELIVERED,
            DeliveryEventType.CANCELLED);

    public DeliveryResponse toResponse(Delivery delivery, List<DeliveryEvent> timeline) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getRestaurantId(),
                delivery.getRestaurantName(),
                toAddress(delivery.getPickupAddress()),
                delivery.getCustomerId(),
                delivery.getCustomerName(),
                delivery.getCustomerPhone(),
                toAddress(delivery.getDropoffAddress()),
                delivery.getPaymentMethod(),
                delivery.getCashToCollect(),
                delivery.getCashCollected(),
                delivery.getCurrency(),
                delivery.getPartnerUserId(),
                delivery.getPartnerName(),
                delivery.getVehicleType(),
                delivery.getCancellationReason(),
                delivery.getReadyAt(),
                delivery.getAssignedAt(),
                delivery.getAcceptedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getCancelledAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                delivery.getVersion(),
                timeline.stream().map(DeliveryMapper::toTimelineEntry).toList());
    }

    public DeliverySummaryResponse toSummary(Delivery delivery, Instant now) {
        return new DeliverySummaryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getRestaurantName(),
                delivery.getPickupAddress() == null ? null : delivery.getPickupAddress().city(),
                delivery.getDropoffAddress() == null ? null : delivery.getDropoffAddress().city(),
                delivery.getPartnerUserId(),
                delivery.getPartnerName(),
                delivery.getCashToCollect(),
                delivery.getReadyAt(),
                delivery.getAssignedAt(),
                delivery.getDeliveredAt(),
                delivery.getStatus().isFinished() ? null : Duration.between(delivery.getReadyAt(), now).toMinutes());
    }

    /** {@code partner} is the delivery's partner, or null; the phone number is shared only while they are on the way. */
    public TrackingResponse toTracking(Delivery delivery, DeliveryPartner partner, List<DeliveryEvent> timeline) {
        boolean onTheWay = delivery.getStatus() == DeliveryStatus.ACCEPTED || delivery.getStatus() == DeliveryStatus.PICKED_UP;
        boolean partnerConfirmed = onTheWay || delivery.getStatus() == DeliveryStatus.DELIVERED;
        return new TrackingResponse(
                delivery.getOrderId(),
                delivery.getId(),
                delivery.getStatus(),
                delivery.getRestaurantName(),
                toAddress(delivery.getDropoffAddress()),
                partnerConfirmed ? delivery.getPartnerName() : null,
                onTheWay && partner != null ? partner.getPhone() : null,
                partnerConfirmed ? delivery.getVehicleType() : null,
                delivery.getCashToCollect(),
                delivery.getCurrency(),
                delivery.getReadyAt(),
                delivery.getAcceptedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getCancelledAt(),
                timeline.stream()
                        .filter(event -> CUSTOMER_STEPS.contains(event.getEventType()))
                        .map(event -> new TrackingResponse.Step(event.getStatus(), event.getNote(), event.getCreatedAt()))
                        .toList());
    }

    /**
     * The live map of a delivery that is ACCEPTED, PICKED_UP or DELIVERED. {@code location} is the partner's latest GPS
     * position or null; nothing else is computed or guessed (no route, distance or arrival time).
     */
    public LiveTrackingResponse toLiveTracking(Delivery delivery, DeliveryPartner partner, RiderLocation location) {
        boolean onTheWay = delivery.getStatus() == DeliveryStatus.ACCEPTED || delivery.getStatus() == DeliveryStatus.PICKED_UP;
        String phase = switch (delivery.getStatus()) {
            case PICKED_UP -> "TO_CUSTOMER";
            case DELIVERED -> "DELIVERED";
            default -> "TO_RESTAURANT";
        };
        LiveTrackingResponse.Rider rider = location == null ? null : new LiveTrackingResponse.Rider(
                new LiveTrackingResponse.GeoPoint(location.getLatitude().doubleValue(), location.getLongitude().doubleValue()),
                location.getHeadingDeg() == null ? null : location.getHeadingDeg().intValue(),
                location.getAccuracyMeters(),
                location.getReceivedAt());
        return new LiveTrackingResponse(
                delivery.getOrderId(),
                LiveTrackingResponse.SOURCE_GPS,
                phase,
                toPlace(delivery.getRestaurantName(), delivery.getPickupAddress()),
                toPlace("Delivery address", delivery.getDropoffAddress()),
                new LiveTrackingResponse.Partner(delivery.getPartnerName(),
                        onTheWay && partner != null ? partner.getPhone() : null, delivery.getVehicleType()),
                rider,
                delivery.getDeliveredAt());
    }

    public PartnerResponse toPartnerResponse(DeliveryPartner partner) {
        return new PartnerResponse(
                partner.getUserId(),
                partner.getFullName(),
                partner.getPhone(),
                partner.getVehicleType(),
                partner.getVehicleNumber(),
                partner.getCity(),
                partner.getStatus(),
                partner.getStatusReason(),
                partner.getCurrentDeliveryId(),
                partner.getAvailableSince(),
                partner.getCompletedDeliveries(),
                partner.isReadyToWork(),
                partner.getCreatedAt(),
                partner.getUpdatedAt(),
                partner.getVersion());
    }

    public RosterPartnerResponse toRosterResponse(DeliveryPartner partner) {
        return new RosterPartnerResponse(
                partner.getUserId(),
                partner.getFullName(),
                partner.getPhone(),
                partner.getVehicleType(),
                partner.getVehicleNumber(),
                partner.getCity(),
                partner.getStatus(),
                partner.getStatusReason(),
                partner.isSuspended(),
                partner.getSuspensionReason(),
                partner.getCurrentDeliveryId(),
                partner.getAvailableSince(),
                partner.getCompletedDeliveries(),
                partner.getVersion());
    }

    /** A place on the live map; its location is null when the address has no map coordinates. */
    private static LiveTrackingResponse.Place toPlace(String name, Address address) {
        if (address == null) {
            return new LiveTrackingResponse.Place(name, "", null);
        }
        String text = Stream.of(address.line1(), address.line2(), address.city())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
        LiveTrackingResponse.GeoPoint location = address.latitude() == null || address.longitude() == null
                ? null
                : new LiveTrackingResponse.GeoPoint(address.latitude().doubleValue(), address.longitude().doubleValue());
        return new LiveTrackingResponse.Place(name, text, location);
    }

    private static AddressResponse toAddress(Address address) {
        return address == null ? null : new AddressResponse(address.line1(), address.line2(), address.city(),
                address.state(), address.postalCode(), address.country(), address.latitude(), address.longitude());
    }

    private static DeliveryResponse.TimelineEntry toTimelineEntry(DeliveryEvent event) {
        return new DeliveryResponse.TimelineEntry(event.getEventType(), event.getStatus(), event.getPartnerUserId(),
                event.getActorType(), event.getActorUserId(), event.getNote(), event.getCreatedAt());
    }
}
