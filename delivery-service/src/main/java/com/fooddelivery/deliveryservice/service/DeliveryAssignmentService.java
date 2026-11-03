package com.fooddelivery.deliveryservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.entity.ActorType;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryEventType;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * assignDeliveryPartner: offers each waiting delivery to the available partner in the restaurant's city who has
 * waited longest. Each delivery is handled in its own short transaction with the delivery and the partner row
 * locked (SKIP LOCKED), so several instances of the service can run the job at the same time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    /** Waiting deliveries looked at per run; plenty for one city cluster, bounded so a run stays short. */
    private static final int MAX_WAITING_PER_RUN = 500;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryWorkflow deliveryWorkflow;
    private final DeliveryDetailsService deliveryDetailsService;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** One run of the job: take back unanswered offers, complete missing details, then assign. */
    public void runAssignmentRound() {
        expireUnansweredOffers();
        deliveryDetailsService.completeWaitingDeliveries();
        assignWaitingDeliveries();
    }

    /**
     * A partner who has not accepted within the accept timeout is probably not looking at the app: the delivery
     * goes back to the queue and the partner goes OFFLINE (they go online again when they are back).
     *
     * @return how many offers were taken back
     */
    public int expireUnansweredOffers() {
        Instant before = clock.instant().minus(appProperties.assignment().acceptTimeout());
        List<Long> ids = deliveryRepository.findIdsAssignedBefore(DeliveryStatus.ASSIGNED, before,
                PageRequest.of(0, appProperties.assignment().batchSize()));
        int expired = 0;
        for (Long id : ids) {
            Boolean done = transactionTemplate.execute(status -> deliveryRepository
                    .lockInStatus(id, DeliveryStatus.ASSIGNED)
                    .filter(delivery -> delivery.getAssignedAt().isBefore(before))
                    .map(delivery -> {
                        DeliveryPartner partner = deliveryPartnerRepository.findById(delivery.getPartnerUserId())
                                .orElseThrow();
                        deliveryWorkflow.unassign(delivery, partner, DeliveryEventType.EXPIRED, PartnerStatus.OFFLINE,
                                ActorType.SYSTEM, null, "No answer from " + partner.getFullName() + " within "
                                        + appProperties.assignment().acceptTimeout().toSeconds() + "s");
                        return true;
                    })
                    .orElse(false));
            if (Boolean.TRUE.equals(done)) {
                expired++;
            }
        }
        return expired;
    }

    /** @return how many deliveries were offered to a partner */
    public int assignWaitingDeliveries() {
        List<Long> ids = deliveryRepository.findIdsReadyToAssign(DeliveryStatus.PENDING,
                PageRequest.of(0, MAX_WAITING_PER_RUN));
        int assigned = 0;
        for (Long id : ids) {
            if (Boolean.TRUE.equals(transactionTemplate.execute(status -> assign(id)))) {
                assigned++;
            }
        }
        if (assigned > 0 || !ids.isEmpty()) {
            log.debug("Assignment run finished. waiting={} assigned={}", ids.size(), assigned);
        }
        return assigned;
    }

    private boolean assign(Long deliveryId) {
        // Still waiting, and not being handled by another instance right now
        Optional<Delivery> waiting = deliveryRepository.lockInStatus(deliveryId, DeliveryStatus.PENDING)
                .filter(Delivery::hasAllDetails);
        if (waiting.isEmpty()) {
            return false;
        }
        Delivery delivery = waiting.get();
        Optional<DeliveryPartner> partner = deliveryPartnerRepository.lockNextAvailable(
                delivery.getPickupAddress().city(), delivery.getId());
        if (partner.isEmpty()) {
            return false;
        }
        deliveryWorkflow.assign(delivery, partner.get(), DeliveryEventType.ASSIGNED, ActorType.SYSTEM, null,
                "Offered to " + partner.get().getFullName());
        return true;
    }
}
