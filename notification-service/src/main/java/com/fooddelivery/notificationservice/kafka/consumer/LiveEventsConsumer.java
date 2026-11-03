package com.fooddelivery.notificationservice.kafka.consumer;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.chat.ChatMessageSent;
import com.fooddelivery.events.chat.ChatMessagesRead;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.notificationservice.service.LiveEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The platform events that become live updates. It reads the same topics as {@link PlatformEventsConsumer} but as its
 * own consumer group, so it never takes events away from the notifications and they never slow it down.
 * <p>
 * Every service instance needs every event (its own connections can belong to any user), so each instance gets a
 * group of its own, made at start-up, and reads only new events: a live update about something that happened while
 * the service was down is of no use. (Kafka removes the unused groups by itself.)
 * <p>
 * Thin, and it never throws: a problem with one live update must not send the event to the dead-letter topics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveEventsConsumer {

    private static final String OWN_GROUP = "#{'${spring.application.name}-live-' + T(java.util.UUID).randomUUID()}";

    private final LiveEventService liveEventService;

    @KafkaListener(topics = Topics.ORDER_STATUS_CHANGED, groupId = OWN_GROUP,
            properties = "auto.offset.reset=latest")
    public void onOrderStatusChanged(OrderStatusChanged event) {
        safely("order status", () -> liveEventService.onOrderStatusChanged(event));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = OWN_GROUP, properties = "auto.offset.reset=latest")
    public void onOrderCancelled(OrderCancelled event) {
        safely("order cancelled", () -> liveEventService.onOrderCancelled(event));
    }

    @KafkaListener(topics = Topics.PAYMENT_STATUS_CHANGED, groupId = OWN_GROUP,
            properties = "auto.offset.reset=latest")
    public void onPaymentStatusChanged(PaymentStatusChanged event) {
        safely("payment status", () -> liveEventService.onPaymentStatusChanged(event));
    }

    @KafkaListener(topics = Topics.DELIVERY_STATUS_CHANGED, groupId = OWN_GROUP,
            properties = "auto.offset.reset=latest")
    public void onDeliveryStatusChanged(DeliveryStatusChanged event) {
        safely("delivery status", () -> liveEventService.onDeliveryStatusChanged(event));
    }

    @KafkaListener(topics = Topics.RESTAURANT_CHANGED, groupId = OWN_GROUP, properties = "auto.offset.reset=latest")
    public void onRestaurantChanged(RestaurantChanged event) {
        safely("restaurant", () -> liveEventService.onRestaurantChanged(event));
    }

    @KafkaListener(topics = Topics.CHAT_MESSAGE_SENT, groupId = OWN_GROUP, properties = "auto.offset.reset=latest")
    public void onChatMessageSent(ChatMessageSent event) {
        safely("chat message", () -> liveEventService.onChatMessageSent(event));
    }

    @KafkaListener(topics = Topics.CHAT_MESSAGES_READ, groupId = OWN_GROUP, properties = "auto.offset.reset=latest")
    public void onChatMessagesRead(ChatMessagesRead event) {
        safely("chat read", () -> liveEventService.onChatMessagesRead(event));
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // a live update is best effort: it must never stop the listener
    private static void safely(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.warn("Live update ({}) failed: {}", what, e.toString());
        }
    }
}
