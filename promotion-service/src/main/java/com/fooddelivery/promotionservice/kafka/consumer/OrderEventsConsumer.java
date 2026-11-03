package com.fooddelivery.promotionservice.kafka.consumer;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.promotionservice.service.OrderEventService;
import com.fooddelivery.promotionservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Order events, for the coupon uses reserved at checkout. Thin: each one is handed to {@link OrderEventService}.
 * A failure is retried, then the event goes to the dead-letter topic (see KafkaConfig).
 */
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final OrderEventService orderEventService;

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreated event) {
        withRequestId(event.getRequestId(), () -> orderEventService.onOrderCreated(event));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelled event) {
        withRequestId(event.getRequestId(), () -> orderEventService.onOrderCancelled(event));
    }

    @KafkaListener(topics = Topics.ORDER_STATUS_CHANGED)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        withRequestId(event.getRequestId(), () -> orderEventService.onOrderStatusChanged(event));
    }

    /** Log lines of the event carry the request id of the request that started it. */
    private static void withRequestId(String requestId, Runnable work) {
        if (requestId != null) {
            MDC.put(RequestIdFilter.MDC_REQUEST_ID, requestId);
        }
        try {
            work.run();
        } finally {
            MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
        }
    }
}
