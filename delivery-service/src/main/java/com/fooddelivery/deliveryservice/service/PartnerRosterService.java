package com.fooddelivery.deliveryservice.service;

import java.util.Locale;
import java.util.Optional;

import com.fooddelivery.deliveryservice.client.DeliveryPartnerServiceClient;
import com.fooddelivery.deliveryservice.client.DeliveryPartnerServiceClient.PartnerProfile;
import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.dto.response.RosterPartnerResponse;
import com.fooddelivery.deliveryservice.entity.ActorType;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryEventType;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.entity.VehicleType;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.kafka.producer.PartnerStatusPublisher;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.events.partner.DeliveryPartnerChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * This service's dispatch list of delivery partners, kept in step with delivery-partner-service, which owns them:
 * from its DeliveryPartnerChanged events, or by asking it when an event has not arrived yet (a partner going online
 * the second they filled in the profile). Newest version wins.
 * <p>
 * A suspension takes the partner out of the assignment at once. An offer they have not accepted yet goes back to
 * the queue; a delivery they already accepted is finished first, then they are SUSPENDED (see DeliveryWorkflow).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerRosterService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryWorkflow deliveryWorkflow;
    private final DeliveryPartnerServiceClient deliveryPartnerServiceClient;
    private final PartnerStatusPublisher partnerStatusPublisher;
    private final ProcessedEventGuard processedEventGuard;
    private final DeliveryMapper deliveryMapper;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void onPartnerChanged(DeliveryPartnerChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "DeliveryPartnerChanged")) {
            return;
        }
        apply(new PartnerProfile(event.getPartnerUserId(), event.getFullName(), event.getPhone(),
                event.getVehicleType(), event.getVehicleNumber(), event.getCity(), event.getAccountStatus(),
                event.getSuspensionReason(), event.getVersion()));
    }

    /**
     * The partner on the dispatch list, added now (asking delivery-partner-service) if needed. Not transactional:
     * the remote call must not hold a database connection.
     *
     * @throws ResourceNotFoundException if the user is not a delivery partner
     */
    public DeliveryPartner findOrRegister(Long userId) {
        return deliveryPartnerRepository.findById(userId).orElseGet(() -> refresh(userId));
    }

    /**
     * Asks delivery-partner-service for the partner as it is now and takes that over (unless this copy is already
     * as new). Not transactional, like {@link #findOrRegister(Long)}.
     */
    public DeliveryPartner refresh(Long userId) {
        PartnerProfile profile = deliveryPartnerServiceClient.findPartner(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " is not a delivery partner"));
        try {
            return transactionTemplate.execute(status -> apply(profile));
        } catch (DataIntegrityViolationException e) {
            // The DeliveryPartnerChanged event added the same partner at this very moment
            return deliveryPartnerRepository.findById(userId).orElseThrow(() -> e);
        }
    }

    /** Internal API: the whole list, for delivery-partner-service's one-off import. Oldest user id first. */
    @Transactional(readOnly = true)
    public PageResponse<RosterPartnerResponse> getRoster(int page, int size) {
        return PageResponse.from(deliveryPartnerRepository.findAll(PageRequest.of(page, size, Sort.by("userId")))
                .map(deliveryMapper::toRosterResponse));
    }

    private DeliveryPartner apply(PartnerProfile profile) {
        Optional<DeliveryPartner> existing = deliveryPartnerRepository.lockById(profile.userId());
        if (existing.isEmpty()) {
            DeliveryPartner partner = DeliveryPartner.register(profile.userId(), profile.fullName(), profile.phone());
            copyProfile(partner, profile);
            DeliveryPartner saved = deliveryPartnerRepository.saveAndFlush(partner);
            log.info("Delivery partner added to the dispatch list. partnerUserId={}", saved.getUserId());
            if (profile.isSuspended()) {
                suspend(saved, profile.suspensionReason());
            }
            return saved;
        }
        DeliveryPartner partner = existing.get();
        if (partner.hasProfileVersion(profile.version())) {
            return partner;
        }
        if (partner.getProfileVersion() == null && profile.version() == 0) {
            // A partner this list had before delivery-partner-service existed, just registered there (UserRegistered
            // replayed, or their first call before the one-off import): that fresh record holds no decisions yet.
            // The list keeps its vehicle, city and suspension until the owner has them (import, or a change there).
            partner.setProfileVersion(0L);
            log.info("Partner from before delivery-partner-service kept as it is until imported. partnerUserId={}",
                    partner.getUserId());
            return deliveryPartnerRepository.saveAndFlush(partner);
        }
        copyProfile(partner, profile);
        if (profile.isSuspended() && !partner.isSuspended()) {
            suspend(partner, profile.suspensionReason());
        } else if (!profile.isSuspended() && partner.isSuspended()) {
            reactivate(partner);
        }
        return deliveryPartnerRepository.saveAndFlush(partner);
    }

    private static void copyProfile(DeliveryPartner partner, PartnerProfile profile) {
        partner.applyProfile(profile.fullName(), profile.phone(), parseVehicleType(profile.vehicleType()),
                profile.vehicleNumber(), profile.city(), profile.version());
    }

    private void suspend(DeliveryPartner partner, String reason) {
        partner.setSuspended(true);
        partner.setSuspensionReason(reason);
        if (partner.getStatus() == PartnerStatus.BUSY) {
            takeBackUnacceptedOffer(partner);
        } else {
            partner.setStatus(PartnerStatus.SUSPENDED);
            partner.setStatusReason(reason);
            partner.setAvailableSince(null);
            publish(partner);
        }
        log.info("Delivery partner suspended: no more deliveries. partnerUserId={} status={}",
                partner.getUserId(), partner.getStatus());
    }

    /**
     * delivery-partner-service refuses to suspend a partner it knows to be on a delivery; an offer made while the
     * suspension was on its way is taken back. A delivery the partner already accepted is finished first.
     */
    private void takeBackUnacceptedOffer(DeliveryPartner partner) {
        Delivery delivery = partner.getCurrentDeliveryId() == null ? null
                : deliveryRepository.findById(partner.getCurrentDeliveryId()).orElse(null);
        if (delivery != null && delivery.getStatus() == DeliveryStatus.ASSIGNED
                && partner.getUserId().equals(delivery.getPartnerUserId())) {
            deliveryWorkflow.unassign(delivery, partner, DeliveryEventType.REASSIGNED, PartnerStatus.AVAILABLE,
                    ActorType.SYSTEM, null, "Taken back: " + partner.getFullName() + " was suspended");
            deliveryRepository.saveAndFlush(delivery);
        } else {
            log.info("Suspended partner finishes the delivery they accepted first. partnerUserId={} deliveryId={}",
                    partner.getUserId(), partner.getCurrentDeliveryId());
        }
    }

    /** A partner still finishing a delivery stays BUSY and becomes AVAILABLE when it is delivered. */
    private void reactivate(DeliveryPartner partner) {
        partner.setSuspended(false);
        partner.setSuspensionReason(null);
        if (partner.getStatus() == PartnerStatus.SUSPENDED) {
            partner.setStatus(PartnerStatus.OFFLINE);
            partner.setStatusReason(null);
            publish(partner);
        }
        log.info("Delivery partner reactivated. partnerUserId={} status={}", partner.getUserId(), partner.getStatus());
    }

    private void publish(DeliveryPartner partner) {
        partnerStatusPublisher.statusChanged(deliveryPartnerRepository.saveAndFlush(partner));
    }

    /** Tolerant reader: a vehicle type this version does not know is left empty (the partner cannot go online). */
    private static VehicleType parseVehicleType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VehicleType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown vehicle type ignored. vehicleType={}", value);
            return null;
        }
    }
}
