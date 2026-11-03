package com.fooddelivery.deliverypartnerservice.service;

import java.util.Locale;

import com.fooddelivery.deliverypartnerservice.entity.PartnerStatus;
import com.fooddelivery.deliverypartnerservice.entity.WorkStatus;
import com.fooddelivery.deliverypartnerservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.events.delivery.DeliveryPartnerStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps each partner's copy of what delivery-service reports (online, on a delivery, deliveries completed), so the
 * partner's profile and the admin's partner list show it. The newest report wins; repeated events are skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkStatusService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final ProcessedEventGuard processedEventGuard;

    @Transactional
    public void onPartnerStatusChanged(DeliveryPartnerStatusChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "DeliveryPartnerStatusChanged")) {
            return;
        }
        PartnerStatus status = parseStatus(event.getStatus());
        if (status == null) {
            log.warn("Unknown partner status ignored. partnerUserId={} status={}", event.getPartnerUserId(),
                    event.getStatus());
            return;
        }
        WorkStatus report = new WorkStatus(event.getVersion(), status, event.getStatusReason(),
                event.getCurrentDeliveryId(), event.getAvailableSince(), event.getCompletedDeliveries());
        deliveryPartnerRepository.lockById(event.getPartnerUserId()).ifPresentOrElse(partner -> {
            if (partner.applyWorkStatus(report)) {
                log.debug("Partner work status copied. partnerUserId={} status={} version={}",
                        partner.getUserId(), status, event.getVersion());
            }
        }, () -> log.warn("Work status of a partner this service does not know (not imported yet?). partnerUserId={}",
                event.getPartnerUserId()));
    }

    /** Tolerant reader: a status this version does not know is reported, not fatal. */
    static PartnerStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PartnerStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
