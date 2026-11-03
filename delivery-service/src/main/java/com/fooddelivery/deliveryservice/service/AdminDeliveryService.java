package com.fooddelivery.deliveryservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.dto.request.DeliverySearchCriteria;
import com.fooddelivery.deliveryservice.dto.request.ReassignDeliveryRequest;
import com.fooddelivery.deliveryservice.dto.response.AuditLogResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliveryReportResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliveryResponse;
import com.fooddelivery.deliveryservice.dto.response.DeliverySummaryResponse;
import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.entity.ActorType;
import com.fooddelivery.deliveryservice.entity.AuditAction;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryEventType;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.exception.ConflictException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryEventRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.DeliverySpecifications;
import com.fooddelivery.deliveryservice.repository.DeliveryStatusTotal;
import com.fooddelivery.deliveryservice.validation.SortValidator;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform admins monitoring deliveries, finding delayed ones and reassigning them. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDeliveryService {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "readyAt", "assignedAt", "deliveredAt",
            "status");

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryWorkflow deliveryWorkflow;
    private final DeliveryMapper deliveryMapper;
    private final AdminAuditService adminAuditService;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<DeliverySummaryResponse> search(DeliverySearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        BusinessDateRange range = BusinessDateRange.of(criteria.fromDate(), criteria.toDate(), businessZone());

        List<Specification<Delivery>> conditions = new ArrayList<>();
        if (criteria.status() != null) {
            conditions.add(DeliverySpecifications.hasStatus(criteria.status()));
        }
        if (criteria.orderId() != null) {
            conditions.add(DeliverySpecifications.forOrder(criteria.orderId()));
        }
        if (criteria.partnerUserId() != null) {
            conditions.add(DeliverySpecifications.forPartner(criteria.partnerUserId()));
        }
        if (criteria.customerId() != null) {
            conditions.add(DeliverySpecifications.forCustomer(criteria.customerId()));
        }
        if (criteria.restaurantId() != null) {
            conditions.add(DeliverySpecifications.forRestaurant(criteria.restaurantId()));
        }
        if (criteria.city() != null && !criteria.city().isBlank()) {
            conditions.add(DeliverySpecifications.inPickupCity(criteria.city().trim()));
        }
        if (range.from() != null) {
            conditions.add(DeliverySpecifications.createdFrom(range.from()));
        }
        if (range.before() != null) {
            conditions.add(DeliverySpecifications.createdBefore(range.before()));
        }
        Instant now = clock.instant();
        return PageResponse.from(deliveryRepository.findAll(Specification.allOf(conditions), pageable)
                .map(delivery -> deliveryMapper.toSummary(delivery, now)));
    }

    /**
     * Unfinished deliveries that are late: still without a partner (or an accepted offer) too long after the food
     * was ready, or not delivered long after it. Longest waiting first.
     */
    @Transactional(readOnly = true)
    public PageResponse<DeliverySummaryResponse> getDelayed(Pageable pageable) {
        Instant now = clock.instant();
        Pageable oldestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("readyAt").and(Sort.by("id")));
        return PageResponse.from(deliveryRepository.findAll(delayed(now), oldestFirst)
                .map(delivery -> deliveryMapper.toSummary(delivery, now)));
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getDelivery(Long deliveryId) {
        Delivery delivery = findDelivery(deliveryId);
        return deliveryMapper.toResponse(delivery, deliveryEventRepository.findByDeliveryIdOrderByIdAsc(deliveryId));
    }

    /**
     * Moves a delivery that is not picked up yet to another partner (who must be AVAILABLE), or, without a
     * partner, back to the queue so the assignment job finds someone else. The previous partner is free again
     * and is not offered this delivery automatically. Audited.
     */
    @Transactional
    public DeliveryResponse reassign(Long deliveryId, ReassignDeliveryRequest request, CallerInfo admin) {
        Delivery delivery = findDelivery(deliveryId);
        if (!delivery.getStatus().isReassignable()) {
            throw new ConflictException("Delivery " + deliveryId + " is " + delivery.getStatus()
                    + ": only deliveries that are not picked up yet can be reassigned");
        }
        DeliveryPartner previous = delivery.getPartnerUserId() == null
                ? null
                : deliveryPartnerRepository.findById(delivery.getPartnerUserId()).orElseThrow();
        String from = previous == null ? "nobody" : previous.getFullName();

        if (request.partnerUserId() == null) {
            if (previous == null) {
                throw new ConflictException("Delivery " + deliveryId + " is already waiting for a partner");
            }
            deliveryWorkflow.unassign(delivery, previous, DeliveryEventType.REASSIGNED, PartnerStatus.AVAILABLE,
                    ActorType.ADMIN, admin.userId(), "Taken from " + from + " by an admin: " + request.reason());
        } else {
            moveToPartner(delivery, previous, request, admin);
        }
        adminAuditService.recordAction(admin, AuditAction.DELIVERY_REASSIGNED, AdminAuditService.ENTITY_TYPE_DELIVERY,
                deliveryId, previous == null ? null : "partner " + previous.getUserId(),
                request.partnerUserId() == null ? "waiting for a partner" : "partner " + request.partnerUserId(),
                request.reason());
        Delivery saved = deliveryRepository.saveAndFlush(delivery);
        return deliveryMapper.toResponse(saved, deliveryEventRepository.findByDeliveryIdOrderByIdAsc(deliveryId));
    }

    /** Hands the delivery to another partner, who must be AVAILABLE; the previous one (if any) is free again. */
    private void moveToPartner(Delivery delivery, DeliveryPartner previous, ReassignDeliveryRequest request,
                               CallerInfo admin) {
        DeliveryPartner next = deliveryPartnerRepository.findById(request.partnerUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Delivery partner " + request.partnerUserId() + " not found"));
        if (previous != null && previous.getUserId().equals(next.getUserId())) {
            throw new ConflictException("Delivery " + delivery.getId() + " is already with partner " + next.getUserId());
        }
        if (next.getStatus() != PartnerStatus.AVAILABLE) {
            throw new ConflictException("Partner " + next.getUserId() + " is " + next.getStatus()
                    + ": only AVAILABLE partners can take a delivery");
        }
        String from = previous == null ? "nobody" : previous.getFullName();
        if (previous != null) {
            deliveryWorkflow.releaseForReassignment(delivery, previous);
        }
        deliveryWorkflow.assign(delivery, next, DeliveryEventType.REASSIGNED, ActorType.ADMIN, admin.userId(),
                "Reassigned from " + from + " to " + next.getFullName() + " by an admin: " + request.reason());
    }

    /** Totals for deliveries created in a period (default: today), average delivery time, and late ones now. */
    @Transactional(readOnly = true)
    public DeliveryReportResponse getReport(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(clock.withZone(businessZone()));
        LocalDate from = fromDate == null ? today : fromDate;
        LocalDate to = toDate == null ? today : toDate;
        BusinessDateRange range = BusinessDateRange.of(from, to, businessZone());

        Map<DeliveryStatus, Long> byStatus = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (DeliveryStatusTotal row : deliveryRepository.countByStatus(range.from(), range.before())) {
            byStatus.put(row.getStatus(), row.getTotal());
        }
        BigDecimal averageMinutes = deliveryRepository.averageDeliveryMinutes(range.from(), range.before());
        return new DeliveryReportResponse(from, to, byStatus,
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus.get(DeliveryStatus.DELIVERED),
                byStatus.get(DeliveryStatus.CANCELLED),
                averageMinutes == null ? null : averageMinutes.setScale(1, RoundingMode.HALF_UP).doubleValue(),
                deliveryRepository.count(delayed(clock.instant())));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Long deliveryId, Pageable pageable) {
        findDelivery(deliveryId);
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_DELIVERY, deliveryId, pageable);
    }

    private Specification<Delivery> delayed(Instant now) {
        AppProperties.Delays delays = appProperties.delays();
        return Specification.anyOf(
                DeliverySpecifications.hasStatusIn(EnumSet.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED))
                        .and(DeliverySpecifications.readyBefore(now.minus(delays.waitingFor()))),
                DeliverySpecifications.hasStatusIn(EnumSet.of(DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP))
                        .and(DeliverySpecifications.readyBefore(now.minus(delays.deliveringFor()))));
    }

    private Delivery findDelivery(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery " + deliveryId + " not found"));
    }

    private ZoneId businessZone() {
        return ZoneId.of(appProperties.businessTimeZone());
    }
}
