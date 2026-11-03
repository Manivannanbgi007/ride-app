package com.fooddelivery.deliveryservice.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.deliveryservice.dto.request.CompleteDeliveryRequest;
import com.fooddelivery.deliveryservice.dto.request.DeclineDeliveryRequest;
import com.fooddelivery.deliveryservice.dto.response.DeliveryResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliverySummaryResponse;
import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.entity.ActorType;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryEventType;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.exception.BusinessRuleException;
import com.fooddelivery.deliveryservice.exception.ConflictException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryEventRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.validation.SortValidator;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A partner working through their deliveries: accept or decline an offer, pick up, deliver. A partner only
 * ever sees deliveries given to them (any other id is "not found").
 */
@Service
@RequiredArgsConstructor
public class PartnerDeliveryService {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "readyAt", "deliveredAt", "status");

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryWorkflow deliveryWorkflow;
    private final DeliveryMapper deliveryMapper;
    private final Clock clock;

    /** The delivery the partner is working on now (offered, accepted or picked up), if any. */
    @Transactional(readOnly = true)
    public Optional<DeliveryResponse> getCurrent(CallerInfo caller) {
        return deliveryPartnerRepository.findById(caller.userId())
                .map(DeliveryPartner::getCurrentDeliveryId)
                .flatMap(id -> deliveryRepository.findByIdAndPartnerUserId(id, caller.userId()))
                .map(this::toResponse);
    }

    /** Delivery history, newest first by default. */
    @Transactional(readOnly = true)
    public PageResponse<DeliverySummaryResponse> getHistory(CallerInfo caller, DeliveryStatus status, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        var page = status == null
                ? deliveryRepository.findByPartnerUserId(caller.userId(), pageable)
                : deliveryRepository.findByPartnerUserIdAndStatus(caller.userId(), status, pageable);
        return PageResponse.from(page.map(delivery -> deliveryMapper.toSummary(delivery, clock.instant())));
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getDelivery(CallerInfo caller, Long deliveryId) {
        return toResponse(findOwn(caller, deliveryId));
    }

    @Transactional
    public DeliveryResponse accept(CallerInfo caller, Long deliveryId) {
        Delivery delivery = findOwn(caller, deliveryId);
        requireStatus(delivery, DeliveryStatus.ASSIGNED, "accepted");
        deliveryWorkflow.accept(delivery);
        return toResponse(deliveryRepository.saveAndFlush(delivery));
    }

    /** Only an offer can be declined; after accepting, a partner who cannot go on calls support (admin reassign). */
    @Transactional
    public void decline(CallerInfo caller, Long deliveryId, DeclineDeliveryRequest request) {
        Delivery delivery = findOwn(caller, deliveryId);
        requireStatus(delivery, DeliveryStatus.ASSIGNED, "declined");
        DeliveryPartner partner = findPartner(caller);
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "" : ": " + request.reason().trim();
        deliveryWorkflow.unassign(delivery, partner, DeliveryEventType.DECLINED, PartnerStatus.AVAILABLE,
                ActorType.DELIVERY_PARTNER, caller.userId(), "Declined by " + partner.getFullName() + reason);
        deliveryRepository.saveAndFlush(delivery);
    }

    @Transactional
    public DeliveryResponse pickUp(CallerInfo caller, Long deliveryId) {
        Delivery delivery = findOwn(caller, deliveryId);
        requireStatus(delivery, DeliveryStatus.ACCEPTED, "picked up");
        deliveryWorkflow.pickUp(delivery);
        return toResponse(deliveryRepository.saveAndFlush(delivery));
    }

    /** Cash on delivery: the partner confirms the exact amount collected. */
    @Transactional
    public DeliveryResponse deliver(CallerInfo caller, Long deliveryId, CompleteDeliveryRequest request) {
        Delivery delivery = findOwn(caller, deliveryId);
        requireStatus(delivery, DeliveryStatus.PICKED_UP, "delivered");
        BigDecimal cashCollected = request == null ? null : request.cashCollected();
        if (delivery.collectsCash()) {
            if (cashCollected == null || cashCollected.compareTo(delivery.getCashToCollect()) != 0) {
                throw new BusinessRuleException("Cash on delivery: collect exactly " + delivery.getCurrency() + " "
                        + delivery.getCashToCollect() + " and confirm the amount in cashCollected");
            }
        } else if (cashCollected != null && cashCollected.signum() != 0) {
            throw new BusinessRuleException("This order is paid online: no cash to collect");
        } else {
            cashCollected = null;
        }
        deliveryWorkflow.deliver(delivery, findPartner(caller), cashCollected);
        return toResponse(deliveryRepository.saveAndFlush(delivery));
    }

    private Delivery findOwn(CallerInfo caller, Long deliveryId) {
        return deliveryRepository.findByIdAndPartnerUserId(deliveryId, caller.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery " + deliveryId + " not found"));
    }

    private DeliveryPartner findPartner(CallerInfo caller) {
        return deliveryPartnerRepository.findById(caller.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User " + caller.userId() + " is not a delivery partner"));
    }

    private static void requireStatus(Delivery delivery, DeliveryStatus expected, String action) {
        if (delivery.getStatus() != expected) {
            throw new ConflictException("Delivery " + delivery.getId() + " is " + delivery.getStatus()
                    + " and cannot be " + action + " now");
        }
    }

    private DeliveryResponse toResponse(Delivery delivery) {
        return deliveryMapper.toResponse(delivery, deliveryEventRepository.findByDeliveryIdOrderByIdAsc(delivery.getId()));
    }
}
