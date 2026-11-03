package com.fooddelivery.deliveryservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.dto.request.RiderLocationRequest;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.RiderLocation;
import com.fooddelivery.deliveryservice.exception.ConflictException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.RiderLocationRepository;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The partner's live GPS position, shared with the customer while the delivery is on its way.
 * <ul>
 *   <li>Only the partner of the delivery can send it, and only while it is ACCEPTED or PICKED_UP.</li>
 *   <li>Only the latest position is kept (no route history); it is deleted soon after the delivery is finished.</li>
 *   <li>Positions are personal data: they are never written to the logs.</li>
 * </ul>
 */
@Slf4j
@Service
public class RiderLocationService {

    /** The statuses in which the partner is on the way and the customer may see where they are. */
    public static final Set<DeliveryStatus> ON_THE_WAY = EnumSet.of(DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP);

    // About 11 cm: more digits than a phone's GPS can really tell
    private static final int DEGREE_SCALE = 6;

    private final DeliveryRepository deliveryRepository;
    private final RiderLocationRepository riderLocationRepository;
    private final AppProperties.LiveLocation settings;
    private final Clock clock;

    public RiderLocationService(DeliveryRepository deliveryRepository, RiderLocationRepository riderLocationRepository,
                                AppProperties appProperties, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.riderLocationRepository = riderLocationRepository;
        this.settings = appProperties.liveLocation();
        this.clock = clock;
    }

    /**
     * Stores the partner's position for their delivery. A position that arrives within the minimum interval after the
     * last one is ignored (the phone may report every second; the map refreshes every few seconds).
     *
     * @throws ResourceNotFoundException another partner's delivery, or no such delivery
     * @throws ConflictException         the delivery is not on its way (not accepted yet, or finished)
     */
    @Transactional
    public void share(CallerInfo caller, Long deliveryId, RiderLocationRequest request) {
        Delivery delivery = deliveryRepository.findByIdAndPartnerUserId(deliveryId, caller.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery " + deliveryId + " not found"));
        if (!ON_THE_WAY.contains(delivery.getStatus())) {
            throw new ConflictException("Delivery " + deliveryId + " is " + delivery.getStatus()
                    + ": the live location is shared only while you are on the way");
        }
        Instant now = clock.instant();
        RiderLocation location = riderLocationRepository.findById(deliveryId)
                .filter(stored -> stored.getPartnerUserId().equals(caller.userId()))
                .orElseGet(() -> new RiderLocation(deliveryId, caller.userId()));
        if (location.isNewerThan(settings.minInterval(), now)) {
            return;
        }
        location.moveTo(degrees(request.latitude()), degrees(request.longitude()), request.accuracyMeters(),
                request.headingDeg() == null ? null : request.headingDeg().shortValue(), now);
        riderLocationRepository.save(location);
        log.debug("Live location updated. deliveryId={} partnerUserId={}", deliveryId, caller.userId());
    }

    /**
     * Deletes the positions of deliveries that are no longer on their way (delivered, cancelled, back in the queue) or
     * now belong to another partner: nobody may see them any more.
     */
    @Scheduled(fixedDelayString = "${app.live-location.cleanup-interval:1m}")
    @Transactional
    public void deleteFinished() {
        List<Long> finished = riderLocationRepository.findNotOnTheWay(ON_THE_WAY);
        if (!finished.isEmpty()) {
            riderLocationRepository.deleteAllByIdInBatch(finished);
            log.info("Live locations of finished deliveries deleted. count={}", finished.size());
        }
    }

    private static BigDecimal degrees(double value) {
        return BigDecimal.valueOf(value).setScale(DEGREE_SCALE, RoundingMode.HALF_UP);
    }
}
