package com.fooddelivery.deliveryservice.service;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.deliveryservice.dto.response.DeliverySummaryResponse;
import com.fooddelivery.deliveryservice.dto.response.PageResponse;
import com.fooddelivery.deliveryservice.dto.response.PartnerAvailabilityResponse;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.PartnerStatusTotal;
import com.fooddelivery.deliveryservice.validation.SortValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform admins looking at what partners are doing: the availability overview and a partner's deliveries.
 * Finding, suspending and reactivating partners is delivery-partner-service's API.
 */
@Service
@RequiredArgsConstructor
public class AdminPartnerService {

    private static final List<String> DELIVERY_SORTABLE_FIELDS = List.of("createdAt", "readyAt", "deliveredAt", "status");

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final Clock clock;

    /** Partners per status right now (who can take deliveries, who is on one, who is offline or suspended). */
    @Transactional(readOnly = true)
    public PartnerAvailabilityResponse getAvailability() {
        Map<PartnerStatus, Long> byStatus = new EnumMap<>(PartnerStatus.class);
        for (PartnerStatus status : PartnerStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (PartnerStatusTotal row : deliveryPartnerRepository.countByStatus()) {
            byStatus.put(row.getStatus(), row.getTotal());
        }
        return new PartnerAvailabilityResponse(byStatus, byStatus.values().stream().mapToLong(Long::longValue).sum());
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliverySummaryResponse> getDeliveries(Long userId, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), DELIVERY_SORTABLE_FIELDS);
        if (!deliveryPartnerRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Delivery partner " + userId + " not found");
        }
        return PageResponse.from(deliveryRepository.findByPartnerUserId(userId, pageable)
                .map(delivery -> deliveryMapper.toSummary(delivery, clock.instant())));
    }
}
