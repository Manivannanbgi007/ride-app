package com.fooddelivery.notificationservice.kafka.consumer;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.events.user.UserRegistered;
import com.fooddelivery.notificationservice.service.EventNotificationService;
import com.fooddelivery.notificationservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The platform events this service turns into notifications. Thin: each one is handed to
 * {@link EventNotificationService}, which only saves notifications (sending happens later, in the dispatcher).
 */
@Component
@RequiredArgsConstructor
public class PlatformEventsConsumer {

    private final EventNotificationService eventNotificationService;

    @KafkaListener(topics = Topics.USER_REGISTERED)
    public void onUserRegistered(UserRegistered event) {
        withRequestId(event.getRequestId(), () -> eventNotificationService.onUserRegistered(event));
    }

    @KafkaListener(topics = Topics.RESTAURANT_CHANGED)
    public void onRestaurantChanged(RestaurantChanged event) {
        withRequestId(event.getRequestId(), () -> eventNotificationService.onRestaurantChanged(event));
    }

    @KafkaListener(topics = Topics.ORDER_STATUS_CHANGED)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        withRequestId(event.getRequestId(), () -> eventNotificationService.onOrderStatusChanged(event));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void onOrderCancelled(OrderCancelled event) {
        withRequestId(event.getRequestId(), () -> eventNotificationService.onOrderCancelled(event));
    }

    @KafkaListener(topics = Topics.PAYMENT_STATUS_CHANGED)
    public void onPaymentStatusChanged(PaymentStatusChanged event) {
        withRequestId(event.getRequestId(), () -> eventNotificationService.onPaymentStatusChanged(event));
    }

    @KafkaListener(topics = Topics.DELIVERY_STATUS_CHANGED)
    public void onDeliveryStatusChanged(DeliveryStatusChanged event) {
        withRequestId(event.getRequestId(), () -> eventNotificationService.onDeliveryStatusChanged(event));
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
