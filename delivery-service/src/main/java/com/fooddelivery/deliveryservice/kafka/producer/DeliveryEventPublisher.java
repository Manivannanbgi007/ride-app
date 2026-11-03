package com.fooddelivery.deliveryservice.kafka.producer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds DeliveryStatusChanged and writes it to the transactional outbox, in the caller's transaction;
 * {@link OutboxRelay} publishes it to Kafka. The message key is the order id, so all events of one
 * order's delivery stay in order.
 */
@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    private static final String AGGREGATE_DELIVERY = "DELIVERY";

    /** Scale of the Avro decimal field (see DeliveryStatusChanged.avsc). */
    private static final int MONEY_SCALE = 2;

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    /** Call only for a delivery with its order details (the event requires the customer). */
    public void statusChanged(Delivery delivery, DeliveryStatus previousStatus, String note) {
        DeliveryStatusChanged event = DeliveryStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setDeliveryId(delivery.getId())
                .setOrderId(delivery.getOrderId())
                .setCustomerId(delivery.getCustomerId())
                .setRestaurantId(delivery.getRestaurantId())
                .setRestaurantName(delivery.getRestaurantName())
                .setStatus(delivery.getStatus().name())
                .setPreviousStatus(previousStatus == null ? null : previousStatus.name())
                .setPartnerUserId(delivery.getPartnerUserId())
                .setPartnerName(delivery.getPartnerName())
                .setVehicleType(delivery.getVehicleType() == null ? null : delivery.getVehicleType().name())
                .setCashCollected(delivery.getCashCollected() == null ? null : money(delivery.getCashCollected()))
                .setNote(note)
                .build();
        outboxWriter.append(AGGREGATE_DELIVERY, delivery.getId(), Topics.DELIVERY_STATUS_CHANGED,
                String.valueOf(delivery.getOrderId()), event);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
