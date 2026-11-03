package com.fooddelivery.deliverypartnerservice.service;

import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.deliverypartnerservice.dto.request.PartnerSearchCriteria;
import com.fooddelivery.deliverypartnerservice.dto.response.AuditLogResponse;
import com.fooddelivery.deliverypartnerservice.dto.response.PageResponse;
import com.fooddelivery.deliverypartnerservice.dto.response.PartnerResponse;
import com.fooddelivery.deliverypartnerservice.entity.AuditAction;
import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import com.fooddelivery.deliverypartnerservice.entity.PartnerStatus;
import com.fooddelivery.deliverypartnerservice.exception.ConflictException;
import com.fooddelivery.deliverypartnerservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher.ChangeType;
import com.fooddelivery.deliverypartnerservice.mapper.PartnerMapper;
import com.fooddelivery.deliverypartnerservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliverypartnerservice.repository.PartnerSpecifications;
import com.fooddelivery.deliverypartnerservice.validation.SortValidator;
import com.fooddelivery.deliverypartnerservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform admins managing delivery partners: search, details, suspend (deactivate) and reactivate. Each change is
 * audited and announced (DeliveryPartnerChanged): delivery-service stops or resumes offering the partner deliveries.
 */
@Service
@RequiredArgsConstructor
public class AdminPartnerService {

    private static final List<String> SORTABLE_FIELDS = List.of("fullName", "city", "status", "availableSince",
            "completedDeliveries", "createdAt");

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final PartnerMapper partnerMapper;
    private final AdminAuditService adminAuditService;
    private final PartnerEventPublisher partnerEventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<PartnerResponse> search(PartnerSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        List<Specification<DeliveryPartner>> conditions = new ArrayList<>();
        if (criteria.status() != null) {
            conditions.add(PartnerSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.city() != null && !criteria.city().isBlank()) {
            conditions.add(PartnerSpecifications.inCity(criteria.city().trim()));
        }
        if (criteria.q() != null && !criteria.q().isBlank()) {
            conditions.add(PartnerSpecifications.matches(criteria.q().trim()));
        }
        return PageResponse.from(deliveryPartnerRepository.findAll(Specification.allOf(conditions), pageable)
                .map(partnerMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PartnerResponse getPartner(Long userId) {
        return partnerMapper.toResponse(deliveryPartnerRepository.findById(userId)
                .orElseThrow(() -> notFound(userId)));
    }

    /** A suspended partner may work again; they start OFFLINE and go online themselves. */
    @Transactional
    public PartnerResponse activate(Long userId, String reason, CallerInfo admin) {
        DeliveryPartner partner = lockPartner(userId);
        if (!partner.isSuspended()) {
            throw new ConflictException("Partner " + userId + " is not suspended (status " + partner.getStatus() + ")");
        }
        partner.activate();
        DeliveryPartner saved = deliveryPartnerRepository.saveAndFlush(partner);
        adminAuditService.recordAction(admin, AuditAction.DELIVERY_PARTNER_ACTIVATED, AdminAuditService.ENTITY_TYPE_PARTNER,
                userId, PartnerStatus.SUSPENDED.name(), saved.getStatus().name(), reason);
        partnerEventPublisher.partnerChanged(saved, ChangeType.ACTIVATED);
        return partnerMapper.toResponse(saved);
    }

    /**
     * Suspends a partner: no more deliveries. A delivery in progress must be reassigned first (delivery-service
     * reports which delivery the partner is on). Should an offer reach the partner while this is on its way,
     * delivery-service takes it back, or lets the partner finish a delivery they already accepted.
     */
    @Transactional
    public PartnerResponse deactivate(Long userId, String reason, CallerInfo admin) {
        DeliveryPartner partner = lockPartner(userId);
        PartnerStatus previous = partner.getStatus();
        if (partner.isSuspended()) {
            throw new ConflictException("Partner " + userId + " is already suspended");
        }
        if (partner.isBusy()) {
            throw new ConflictException("Partner " + userId + " is on delivery " + partner.getCurrentDeliveryId()
                    + ": reassign it first");
        }
        partner.suspend(reason);
        DeliveryPartner saved = deliveryPartnerRepository.saveAndFlush(partner);
        adminAuditService.recordAction(admin, AuditAction.DELIVERY_PARTNER_DEACTIVATED, AdminAuditService.ENTITY_TYPE_PARTNER,
                userId, previous.name(), PartnerStatus.SUSPENDED.name(), reason);
        partnerEventPublisher.partnerChanged(saved, ChangeType.SUSPENDED);
        return partnerMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Long userId, Pageable pageable) {
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_PARTNER, userId, pageable);
    }

    private DeliveryPartner lockPartner(Long userId) {
        return deliveryPartnerRepository.lockById(userId).orElseThrow(() -> notFound(userId));
    }

    private static ResourceNotFoundException notFound(Long userId) {
        return new ResourceNotFoundException("Delivery partner " + userId + " not found");
    }
}
