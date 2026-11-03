package com.fooddelivery.deliverypartnerservice.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fooddelivery.deliverypartnerservice.client.DeliveryServiceClient;
import com.fooddelivery.deliverypartnerservice.client.DeliveryServiceClient.LegacyPartner;
import com.fooddelivery.deliverypartnerservice.client.DeliveryServiceClient.LegacyPartnerPage;
import com.fooddelivery.deliverypartnerservice.dto.response.PartnerImportResponse;
import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import com.fooddelivery.deliverypartnerservice.entity.PartnerStatus;
import com.fooddelivery.deliverypartnerservice.entity.VehicleType;
import com.fooddelivery.deliverypartnerservice.entity.WorkStatus;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher.ChangeType;
import com.fooddelivery.deliverypartnerservice.repository.DeliveryPartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Takes over the partners delivery-service kept before this service existed: profile, account status and the work
 * status they had. Needed once on an existing installation (a new one has nothing to take over); running it again
 * is safe.
 * <ul>
 *   <li>A partner this service does not have is added as delivery-service has them. Nothing is published:
 *       delivery-service already has the same data.</li>
 *   <li>A partner registered here meanwhile but never changed (version 0: UserRegistered replayed, or their first
 *       call before the import) is completed from delivery-service and published (IMPORTED), so both sides
 *       agree again. delivery-service keeps its own data for such partners until then.</li>
 *   <li>A partner changed here since (a profile update, a suspension) is left as it is: this service knows better.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyPartnerImportService {

    private static final int PAGE_SIZE = 100;

    private final DeliveryServiceClient deliveryServiceClient;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final PartnerEventPublisher partnerEventPublisher;
    private final TransactionTemplate transactionTemplate;

    private enum Outcome {
        IMPORTED,
        COMPLETED,
        SKIPPED
    }

    /** Not transactional: each partner is saved in its own short transaction, between the calls to delivery-service. */
    public PartnerImportResponse importFromDeliveryService() {
        long read = 0;
        long imported = 0;
        long completed = 0;
        int page = 0;
        boolean lastPage;
        do {
            LegacyPartnerPage result = deliveryServiceClient.findPartners(page++, PAGE_SIZE);
            List<LegacyPartner> partners = result == null || result.content() == null ? List.of() : result.content();
            for (LegacyPartner legacy : partners) {
                read++;
                Outcome outcome = transactionTemplate.execute(status -> importOne(legacy));
                if (outcome == Outcome.IMPORTED) {
                    imported++;
                } else if (outcome == Outcome.COMPLETED) {
                    completed++;
                }
            }
            lastPage = result == null || result.last() || partners.isEmpty();
        } while (!lastPage);
        log.info("Delivery partners taken over from delivery-service. read={} imported={} completed={}",
                read, imported, completed);
        return new PartnerImportResponse(read, imported, completed, read - imported - completed);
    }

    private Outcome importOne(LegacyPartner legacy) {
        Optional<DeliveryPartner> existing = deliveryPartnerRepository.lockById(legacy.userId());
        if (existing.isEmpty()) {
            DeliveryPartner partner = DeliveryPartner.register(legacy.userId(), legacy.fullName(), legacy.phone());
            copy(partner, legacy);
            deliveryPartnerRepository.save(partner);
            return Outcome.IMPORTED;
        }
        DeliveryPartner partner = existing.get();
        if (partner.getVersion() != 0) {
            return Outcome.SKIPPED;
        }
        copy(partner, legacy);
        DeliveryPartner saved = deliveryPartnerRepository.saveAndFlush(partner);
        if (saved.getVersion() == 0) {
            // delivery-service knew nothing more about this partner
            return Outcome.SKIPPED;
        }
        partnerEventPublisher.partnerChanged(saved, ChangeType.IMPORTED);
        return Outcome.COMPLETED;
    }

    /** Profile, the work status (with delivery-service's version, so its later reports apply) and the suspension. */
    private static void copy(DeliveryPartner partner, LegacyPartner legacy) {
        partner.updateProfile(parseVehicleType(legacy.vehicleType()), legacy.vehicleNumber(), legacy.city());
        PartnerStatus status = WorkStatusService.parseStatus(legacy.status());
        partner.applyWorkStatus(new WorkStatus(legacy.version(), status == null ? PartnerStatus.OFFLINE : status,
                legacy.statusReason(), legacy.currentDeliveryId(), legacy.availableSince(), legacy.completedDeliveries()));
        if (legacy.suspended() && !partner.isSuspended()) {
            partner.suspend(legacy.suspensionReason());
        }
    }

    private static VehicleType parseVehicleType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VehicleType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown vehicle type left empty. vehicleType={}", value);
            return null;
        }
    }
}
