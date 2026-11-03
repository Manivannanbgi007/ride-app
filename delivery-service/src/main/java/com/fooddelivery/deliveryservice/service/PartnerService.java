package com.fooddelivery.deliveryservice.service;

import java.time.Clock;

import com.fooddelivery.deliveryservice.dto.request.ChangeAvailabilityRequest;
import com.fooddelivery.deliveryservice.dto.response.PartnerResponse;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.exception.BusinessRuleException;
import com.fooddelivery.deliveryservice.exception.ConflictException;
import com.fooddelivery.deliveryservice.exception.InvalidRequestException;
import com.fooddelivery.deliveryservice.kafka.producer.PartnerStatusPublisher;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A delivery partner going online (AVAILABLE) or offline. Decided here, together with the deliveries; the profile
 * (vehicle and city) is delivery-partner-service's API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final PartnerRosterService partnerRosterService;
    private final PartnerStatusPublisher partnerStatusPublisher;
    private final DeliveryMapper deliveryMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Going online (AVAILABLE) or offline. While on a delivery the partner is BUSY and stays so until it is
     * delivered; a suspended partner cannot go online. Not transactional: a partner this service has no (current)
     * copy of is asked of delivery-partner-service first.
     */
    public PartnerResponse changeAvailability(CallerInfo caller, ChangeAvailabilityRequest request) {
        PartnerStatus target = request.status();
        if (target != PartnerStatus.AVAILABLE && target != PartnerStatus.OFFLINE) {
            throw new InvalidRequestException("Partners can only switch between AVAILABLE and OFFLINE");
        }
        DeliveryPartner known = partnerRosterService.findOrRegister(caller.userId());
        if (target == PartnerStatus.AVAILABLE && (!known.isReadyToWork() || known.isSuspended())) {
            // The profile was perhaps filled in (or the partner reactivated) a moment ago, its event still on the way
            partnerRosterService.refresh(caller.userId());
        }
        return transactionTemplate.execute(status -> {
            DeliveryPartner partner = deliveryPartnerRepository.lockById(caller.userId()).orElseThrow();
            if (partner.isSuspended()) {
                throw new ConflictException(
                        "Your account is suspended: " + partner.getSuspensionReason() + ". Please contact support.");
            }
            if (partner.getStatus() == PartnerStatus.BUSY) {
                throw new ConflictException("Finish delivery " + partner.getCurrentDeliveryId() + " first");
            }
            if (target == PartnerStatus.AVAILABLE && !partner.isReadyToWork()) {
                throw new BusinessRuleException("Add your vehicle and city before going online");
            }
            if (partner.getStatus() != target) {
                partner.setStatus(target);
                partner.setStatusReason(null);
                partner.setAvailableSince(target == PartnerStatus.AVAILABLE ? clock.instant() : null);
                partnerStatusPublisher.statusChanged(deliveryPartnerRepository.saveAndFlush(partner));
                log.info("Partner availability changed. partnerUserId={} status={}", partner.getUserId(), target);
            }
            return deliveryMapper.toPartnerResponse(partner);
        });
    }
}
