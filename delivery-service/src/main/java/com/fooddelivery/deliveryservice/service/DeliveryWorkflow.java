package com.fooddelivery.deliveryservice.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import com.fooddelivery.deliveryservice.entity.ActorType;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryEvent;
import com.fooddelivery.deliveryservice.entity.DeliveryEventType;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.exception.ConflictException;
import com.fooddelivery.deliveryservice.kafka.producer.DeliveryEventPublisher;
import com.fooddelivery.deliveryservice.kafka.producer.PartnerStatusPublisher;
import com.fooddelivery.deliveryservice.repository.DeliveryEventRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place where a delivery's status, and the matching partner status, change. Every change is checked
 * against {@link DeliveryStatus} rules, written to the delivery timeline and published as DeliveryStatusChanged
 * (transactional outbox), all in the caller's transaction. A partner whose status changes is published as
 * DeliveryPartnerStatusChanged (delivery-partner-service shows it).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryWorkflow {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryEventPublisher deliveryEventPublisher;
    private final PartnerStatusPublisher partnerStatusPublisher;
    private final DeliveryMetrics deliveryMetrics;
    private final Clock clock;

    /** Saves a new PENDING delivery (the food is ready). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Delivery create(Delivery delivery, String note) {
        delivery.setStatus(DeliveryStatus.PENDING);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);
        recordEvent(saved, null, DeliveryEventType.CREATED, null, ActorType.SYSTEM, null, note);
        log.info("Delivery created. deliveryId={} orderId={}", saved.getId(), saved.getOrderId());
        return saved;
    }

    /**
     * Gives the delivery to a partner, who becomes BUSY. {@code type} is ASSIGNED (the assignment job)
     * or REASSIGNED (an admin, possibly taking it from another partner, who must be released first).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assign(Delivery delivery, DeliveryPartner partner, DeliveryEventType type, ActorType actor,
                       Long actorUserId, String note) {
        DeliveryStatus previous = change(delivery, DeliveryStatus.ASSIGNED);
        Instant now = clock.instant();
        delivery.setPartnerUserId(partner.getUserId());
        delivery.setPartnerName(partner.getFullName());
        delivery.setVehicleType(partner.getVehicleType());
        delivery.setAssignedAt(now);
        delivery.setAcceptedAt(null);
        partner.setStatus(PartnerStatus.BUSY);
        partner.setCurrentDeliveryId(delivery.getId());
        partner.setStatusReason(null);
        publishPartner(partner);
        recordEvent(delivery, previous, type, partner.getUserId(), actor, actorUserId, note);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accept(Delivery delivery) {
        DeliveryStatus previous = change(delivery, DeliveryStatus.ACCEPTED);
        delivery.setAcceptedAt(clock.instant());
        recordEvent(delivery, previous, DeliveryEventType.ACCEPTED, delivery.getPartnerUserId(), ActorType.DELIVERY_PARTNER,
                delivery.getPartnerUserId(), delivery.getPartnerName() + " is on the way to the restaurant");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void pickUp(Delivery delivery) {
        DeliveryStatus previous = change(delivery, DeliveryStatus.PICKED_UP);
        delivery.setPickedUpAt(clock.instant());
        recordEvent(delivery, previous, DeliveryEventType.PICKED_UP, delivery.getPartnerUserId(), ActorType.DELIVERY_PARTNER,
                delivery.getPartnerUserId(), "Picked up from " + delivery.getRestaurantName());
    }

    /** Delivered: the partner is free again and counts one more completed delivery. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deliver(Delivery delivery, DeliveryPartner partner, BigDecimal cashCollected) {
        DeliveryStatus previous = change(delivery, DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(clock.instant());
        delivery.setCashCollected(cashCollected);
        partner.setCompletedDeliveries(partner.getCompletedDeliveries() + 1);
        release(partner, PartnerStatus.AVAILABLE, null);
        String note = cashCollected == null ? "Delivered" : "Delivered, cash collected: " + cashCollected;
        recordEvent(delivery, previous, DeliveryEventType.DELIVERED, partner.getUserId(), ActorType.DELIVERY_PARTNER,
                partner.getUserId(), note);
        deliveryMetrics.delivered(delivery);
    }

    /**
     * Takes the delivery away from its partner and puts it back in the queue (PENDING): the partner declined
     * (back to AVAILABLE), did not answer (OFFLINE) or an admin moved it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void unassign(Delivery delivery, DeliveryPartner partner, DeliveryEventType type, PartnerStatus partnerStatus,
                         ActorType actor, Long actorUserId, String note) {
        DeliveryStatus previous = change(delivery, DeliveryStatus.PENDING);
        clearPartner(delivery);
        release(partner, partnerStatus, partnerStatus == PartnerStatus.OFFLINE ? note : null);
        recordEvent(delivery, previous, type, partner.getUserId(), actor, actorUserId, note);
    }

    /** The order was cancelled; a partner who was on it is free again ({@code partner} is null if there was none). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Delivery delivery, DeliveryPartner partner, String reason) {
        DeliveryStatus previous = change(delivery, DeliveryStatus.CANCELLED);
        delivery.setCancelledAt(clock.instant());
        delivery.setCancellationReason(reason);
        if (partner != null) {
            release(partner, PartnerStatus.AVAILABLE, null);
        }
        String note = previous == DeliveryStatus.PICKED_UP
                ? "Order cancelled after pickup: return the food to the restaurant. " + reason
                : reason;
        recordEvent(delivery, previous, DeliveryEventType.CANCELLED, delivery.getPartnerUserId(), ActorType.SYSTEM, null, note);
        deliveryMetrics.cancelled();
    }

    /** Before reassigning: the current partner is free again (the delivery keeps its status for a moment). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForReassignment(Delivery delivery, DeliveryPartner partner) {
        clearPartner(delivery);
        release(partner, PartnerStatus.AVAILABLE, null);
    }

    private DeliveryStatus change(Delivery delivery, DeliveryStatus next) {
        DeliveryStatus previous = delivery.getStatus();
        if (!previous.canChangeTo(next)) {
            throw new ConflictException("Delivery " + delivery.getId() + " cannot change from " + previous + " to " + next);
        }
        delivery.setStatus(next);
        return previous;
    }

    private void recordEvent(Delivery delivery, DeliveryStatus previous, DeliveryEventType type, Long partnerUserId,
                             ActorType actor, Long actorUserId, String note) {
        deliveryEventRepository.save(new DeliveryEvent(delivery.getId(), type, delivery.getStatus(), partnerUserId,
                actor, actorUserId, note, clock.instant()));
        // A delivery whose order details are still on the way is not announced yet (the event needs the customer);
        // it cannot reach a partner before they arrive.
        if (delivery.hasOrderDetails()) {
            deliveryEventPublisher.statusChanged(delivery, previous, note);
        }
        log.info("Delivery event. deliveryId={} orderId={} event={} status={} partnerUserId={} actor={}",
                delivery.getId(), delivery.getOrderId(), type, delivery.getStatus(), partnerUserId, actor);
    }

    private static void clearPartner(Delivery delivery) {
        delivery.setPartnerUserId(null);
        delivery.setPartnerName(null);
        delivery.setVehicleType(null);
        delivery.setAssignedAt(null);
        delivery.setAcceptedAt(null);
    }

    private void release(DeliveryPartner partner, PartnerStatus next, String reason) {
        partner.setCurrentDeliveryId(null);
        if (partner.isSuspended()) {
            // Suspended (in delivery-partner-service) while on this delivery: no more work after it
            partner.setStatus(PartnerStatus.SUSPENDED);
            partner.setStatusReason(partner.getSuspensionReason());
            partner.setAvailableSince(null);
        } else {
            partner.setStatus(next);
            partner.setStatusReason(reason);
            if (next == PartnerStatus.AVAILABLE) {
                // Back of the queue: partners who waited longer get the next delivery first
                partner.setAvailableSince(clock.instant());
            }
        }
        publishPartner(partner);
    }

    /** Flushed first, so the event carries the partner's new version (delivery-partner-service keeps the newest). */
    private void publishPartner(DeliveryPartner partner) {
        partnerStatusPublisher.statusChanged(deliveryPartnerRepository.saveAndFlush(partner));
    }
}
