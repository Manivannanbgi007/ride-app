package com.fooddelivery.notificationservice.service;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fooddelivery.events.chat.ChatMessageSent;
import com.fooddelivery.events.chat.ChatMessagesRead;
import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.notificationservice.config.RealtimeProperties;
import com.fooddelivery.notificationservice.dto.response.LiveEvent;
import com.fooddelivery.notificationservice.dto.response.LiveMessage;
import com.fooddelivery.notificationservice.web.LiveSessions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a platform event (read from Kafka) into a small "something changed" message and sends it to the open live
 * connections that may see it ({@link LiveAudience}). It reads and writes no database and calls no other service:
 * the events already say who they are about, and the application fetches the current data over REST.
 * <p>
 * The same event is sent once: Kafka may deliver an event twice, so recent event ids are remembered.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveEventService {

    private final LiveSessions sessions;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    /** The latest event ids, oldest first; the oldest is forgotten when the limit is reached. */
    private final Map<String, Boolean> recentEventIds;

    public LiveEventService(LiveSessions sessions, JsonMapper jsonMapper, Clock clock, RealtimeProperties properties) {
        this.sessions = sessions;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        int limit = properties.recentEventsKept();
        this.recentEventIds = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > limit;
            }
        });
    }

    public void onOrderStatusChanged(OrderStatusChanged event) {
        publish(new LiveEvent(event.getEventId(), "ORDER_STATUS_CHANGED", event.getOccurredAt(), event.getOrderId(),
                null, null, event.getRestaurantId(), event.getStatus(), event.getPreviousStatus()),
                new LiveAudience(event.getCustomerId(), null, null, event.getRestaurantId()));
    }

    public void onOrderCancelled(OrderCancelled event) {
        publish(new LiveEvent(event.getEventId(), "ORDER_CANCELLED", event.getOccurredAt(), event.getOrderId(), null,
                null, event.getRestaurantId(), event.getFinalStatus(), event.getPreviousStatus()),
                new LiveAudience(event.getCustomerId(), null, null, event.getRestaurantId()));
    }

    /** Payments are the customer's and the admins' business: a restaurant owner does not see them. */
    public void onPaymentStatusChanged(PaymentStatusChanged event) {
        publish(new LiveEvent(event.getEventId(), "PAYMENT_STATUS_CHANGED", event.getOccurredAt(), event.getOrderId(),
                null, event.getPaymentId(), null, event.getStatus(), null),
                new LiveAudience(event.getCustomerId(), null, null, null));
    }

    public void onDeliveryStatusChanged(DeliveryStatusChanged event) {
        publish(new LiveEvent(event.getEventId(), "DELIVERY_STATUS_CHANGED", event.getOccurredAt(), event.getOrderId(),
                event.getDeliveryId(), null, event.getRestaurantId(), event.getStatus(), event.getPreviousStatus()),
                new LiveAudience(event.getCustomerId(), null, event.getPartnerUserId(), event.getRestaurantId()));
    }

    /**
     * The owner's restaurants, as they change (submitted, approved, suspended...). A new restaurant of an owner
     * who is connected right now is added to what they see, so its first orders reach them without reconnecting.
     */
    public void onRestaurantChanged(RestaurantChanged event) {
        sessions.grantRestaurant(event.getOwnerUserId(), event.getRestaurantId());
        publish(new LiveEvent(event.getEventId(), "RESTAURANT_CHANGED", event.getOccurredAt(), null, null, null,
                event.getRestaurantId(), event.getStatus(), event.getPreviousStatus()),
                new LiveAudience(null, event.getOwnerUserId(), null, event.getRestaurantId()));
    }

    /** A new support-chat message: ids only, the text is read over REST. Participant and platform admins. */
    public void onChatMessageSent(ChatMessageSent event) {
        publish(new LiveEvent(event.getEventId(), "CHAT_MESSAGE", event.getOccurredAt(), null, null, null, null,
                null, null, event.getConversationId(), event.getMessageId(), event.getFromStaff(), null, null),
                LiveAudience.chat(event.getParticipantUserId()));
    }

    /** Messages of a support chat were read. Participant and platform admins. */
    public void onChatMessagesRead(ChatMessagesRead event) {
        publish(new LiveEvent(event.getEventId(), "CHAT_READ", event.getOccurredAt(), null, null, null, null,
                null, null, event.getConversationId(), null, null, event.getReadByStaff(), event.getUpToMessageId()),
                LiveAudience.chat(event.getParticipantUserId()));
    }

    private void publish(LiveEvent event, LiveAudience audience) {
        if (recentEventIds.put(event.id(), Boolean.TRUE) != null) {
            log.debug("Live event {} already sent: skipped", event.id());
            return;
        }
        if (sessions.size() == 0) {
            return; // nobody is connected to this instance: nothing to build or send
        }
        try {
            String json = jsonMapper.writeValueAsString(LiveMessage.of(event, clock.instant()));
            int receivers = sessions.send(audience::includes, json);
            log.debug("Live event {} ({}) sent to {} connection(s)", event.id(), event.type(), receivers);
        } catch (JacksonException e) {
            // A live update is a convenience: the REST data is right, and the next refresh shows it
            log.warn("Live event {} could not be written as JSON: {}", event.id(), e.getMessage());
        }
    }
}
