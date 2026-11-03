package com.fooddelivery.deliverypartnerservice.kafka.producer;

import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import com.fooddelivery.deliverypartnerservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.partner.DeliveryPartnerChanged;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds DeliveryPartnerChanged and writes it to the transactional outbox, in the caller's transaction;
 * {@link OutboxRelay} publishes it to Kafka. delivery-service keeps its copy of partners (who can be offered
 * deliveries, in which city) from these events. The message key is the partner's user id, so a partner's events
 * stay in order.
 */
@Component
@RequiredArgsConstructor
public class PartnerEventPublisher {

    private static final String AGGREGATE_PARTNER = "DELIVERY_PARTNER";

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    /** What happened to the partner. */
    public enum ChangeType {
        REGISTERED,
        PROFILE_UPDATED,
        SUSPENDED,
        ACTIVATED,
        /** Completed from what delivery-service kept before this service existed (one-off import). */
        IMPORTED
    }

    /** Call after the change is flushed, so the event carries the partner's new version. */
    public void partnerChanged(DeliveryPartner partner, ChangeType changeType) {
        DeliveryPartnerChanged event = DeliveryPartnerChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setChangeType(changeType.name())
                .setPartnerUserId(partner.getUserId())
                .setVersion(partner.getVersion())
                .setFullName(partner.getFullName())
                .setPhone(partner.getPhone())
                .setVehicleType(partner.getVehicleType() == null ? null : partner.getVehicleType().name())
                .setVehicleNumber(partner.getVehicleNumber())
                .setCity(partner.getCity())
                .setAccountStatus(partner.getAccountStatus().name())
                .setSuspensionReason(partner.getSuspensionReason())
                .build();
        outboxWriter.append(AGGREGATE_PARTNER, partner.getUserId(), Topics.DELIVERY_PARTNER_CHANGED,
                String.valueOf(partner.getUserId()), event);
    }
}
