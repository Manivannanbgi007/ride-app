package com.fooddelivery.orderservice.kafka.consumer;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.orderservice.service.OrderDeliveryService;
import com.fooddelivery.orderservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Delivery progress from delivery-service (picked up, delivered). A failure (for example the database is down, or the order was
 * changed at the same moment) is retried, then the event goes to the dead-letter topic (see KafkaConfig).
 */
@Component
@RequiredArgsConstructor
public class DeliveryStatusChangedConsumer {

    private final OrderDeliveryService orderDeliveryService;

    @KafkaListener(topics = Topics.DELIVERY_STATUS_CHANGED)
    public void onDeliveryStatusChanged(DeliveryStatusChanged event) {
        // Log lines of this event carry the request id of the request that caused it.
        if (event.getRequestId() != null) {
            MDC.put(RequestIdFilter.MDC_REQUEST_ID, event.getRequestId());
        }
        try {
            orderDeliveryService.applyDeliveryStatusChanged(event);
        } finally {
            MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
        }
    }
}
