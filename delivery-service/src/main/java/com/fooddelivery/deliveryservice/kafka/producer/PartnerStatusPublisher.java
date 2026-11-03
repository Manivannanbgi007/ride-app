package com.fooddelivery.deliveryservice.kafka.producer;

import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.delivery.DeliveryPartnerStatusChanged;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds DeliveryPartnerStatusChanged and writes it to the transactional outbox, in the caller's transaction;
 * {@link OutboxRelay} publishes it to Kafka. delivery-partner-service shows partners with this status (it owns the
 * partners, this service decides what they are doing). The message key is the partner's user id, so a partner's
 * events stay in order.
 */
@Component
@RequiredArgsConstructor
public class PartnerStatusPublisher {

    private static final String AGGREGATE_PARTNER = "DELIVERY_PARTNER";

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    /** Call after the partner is flushed, so the event carries the record's new version. */
    public void statusChanged(DeliveryPartner partner) {
        DeliveryPartnerStatusChanged event = DeliveryPartnerStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setPartnerUserId(partner.getUserId())
                .setVersion(partner.getVersion())
                .setStatus(partner.getStatus().name())
                .setStatusReason(partner.getStatusReason())
                .setCurrentDeliveryId(partner.getCurrentDeliveryId())
                .setAvailableSince(partner.getAvailableSince())
                .setCompletedDeliveries(partner.getCompletedDeliveries())
                .build();
        outboxWriter.append(AGGREGATE_PARTNER, partner.getUserId(), Topics.DELIVERY_PARTNER_STATUS_CHANGED,
                String.valueOf(partner.getUserId()), event);
    }
}
