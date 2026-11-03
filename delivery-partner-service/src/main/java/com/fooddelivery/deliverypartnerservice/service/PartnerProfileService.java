package com.fooddelivery.deliverypartnerservice.service;

import com.fooddelivery.deliverypartnerservice.dto.request.UpdatePartnerProfileRequest;
import com.fooddelivery.deliverypartnerservice.dto.response.InternalPartnerResponse;
import com.fooddelivery.deliverypartnerservice.dto.response.PartnerResponse;
import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher.ChangeType;
import com.fooddelivery.deliverypartnerservice.mapper.PartnerMapper;
import com.fooddelivery.deliverypartnerservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliverypartnerservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A delivery partner's own profile. Going online or offline is delivery-service's API: it decides availability
 * together with the deliveries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerProfileService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final PartnerRegistrationService partnerRegistrationService;
    private final PartnerEventPublisher partnerEventPublisher;
    private final PartnerMapper partnerMapper;
    private final TransactionTemplate transactionTemplate;

    /** Not transactional: registering an unknown partner asks user-service first (see PartnerRegistrationService). */
    public PartnerResponse getProfile(CallerInfo caller) {
        return partnerMapper.toResponse(partnerRegistrationService.findOrRegister(caller.userId()));
    }

    /**
     * Internal API for delivery-service: a partner whose DeliveryPartnerChanged event it has not received yet (or
     * whose profile it has an older copy of). Registers the partner first if needed, like the partner's own call.
     */
    public InternalPartnerResponse getForDelivery(Long userId) {
        return partnerMapper.toInternalResponse(partnerRegistrationService.findOrRegister(userId));
    }

    /** Vehicle and city, required before going online; delivery-service learns them from DeliveryPartnerChanged. */
    public PartnerResponse updateProfile(CallerInfo caller, UpdatePartnerProfileRequest request) {
        partnerRegistrationService.findOrRegister(caller.userId());
        return transactionTemplate.execute(status -> {
            DeliveryPartner partner = deliveryPartnerRepository.lockById(caller.userId()).orElseThrow();
            boolean changed = partner.updateProfile(request.vehicleType(),
                    request.vehicleNumber().trim().toUpperCase(), request.city().trim());
            DeliveryPartner saved = deliveryPartnerRepository.saveAndFlush(partner);
            if (changed) {
                partnerEventPublisher.partnerChanged(saved, ChangeType.PROFILE_UPDATED);
                log.info("Partner profile updated. partnerUserId={} city={} vehicleType={}",
                        saved.getUserId(), saved.getCity(), saved.getVehicleType());
            }
            return partnerMapper.toResponse(saved);
        });
    }
}
