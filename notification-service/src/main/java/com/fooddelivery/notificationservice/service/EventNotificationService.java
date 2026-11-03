package com.fooddelivery.notificationservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.events.user.UserRegistered;
import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationType;
import com.fooddelivery.notificationservice.repository.NotificationRepository;
import com.fooddelivery.notificationservice.template.Message;
import com.fooddelivery.notificationservice.template.NotificationTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Decides who is notified about a platform event, how (channels), and with which text. It only saves the
 * notifications (PENDING); {@link NotificationDispatcher} sends them. Nothing here calls another service,
 * so a slow mail server or user-service never holds up the Kafka consumers.
 * <p>
 * Each event is applied once: its id is recorded in the same transaction ({@link ProcessedEventGuard}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventNotificationService {

    /** Orders and deliveries both have this status. */
    private static final String ACCEPTED = "ACCEPTED";
    private static final Set<String> STATUSES_THE_RESTAURANT_WORKS_ON =
            Set.of("PLACED", ACCEPTED, "PREPARING", "READY_FOR_PICKUP");
    /** Delivery statuses in which a partner is on the delivery. */
    private static final Set<String> PARTNER_STATUSES = Set.of("ASSIGNED", ACCEPTED, "PICKED_UP");

    private final NotificationRepository notificationRepository;
    private final ProcessedEventGuard processedEventGuard;
    private final NotificationTemplates templates;
    private final Clock clock;

    /** Welcome email, and a text message when the user gave a phone number. */
    @Transactional
    public void onUserRegistered(UserRegistered event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "UserRegistered")) {
            return;
        }
        Message message = templates.welcome(event.getFullName(), event.getRole());
        // The address is in the event: no lookup needed when sending.
        save(event.getEventId(), NotificationType.WELCOME, NotificationChannel.EMAIL, event.getUserId(), null, null,
                event.getEmail(), message, event.getRequestId());
        if (StringUtils.hasText(event.getPhone())) {
            save(event.getEventId(), NotificationType.WELCOME, NotificationChannel.SMS, event.getUserId(), null, null,
                    event.getPhone(), message, event.getRequestId());
        }
    }

    /** The owner hears about the review of their restaurant. Profile edits notify nobody. */
    @Transactional
    public void onRestaurantChanged(RestaurantChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "RestaurantChanged")) {
            return;
        }
        String name = event.getName();
        boolean submittedOrResubmitted = "SUBMITTED".equals(event.getChangeType())
                || ("STATUS_CHANGED".equals(event.getChangeType()) && "PENDING".equals(event.getStatus()));
        NotificationType type;
        Message message;
        if (submittedOrResubmitted) {
            type = NotificationType.RESTAURANT_SUBMITTED;
            message = templates.restaurantSubmitted(name, !"SUBMITTED".equals(event.getChangeType()));
        } else if ("STATUS_CHANGED".equals(event.getChangeType())) {
            switch (event.getStatus()) {
                case "APPROVED" -> {
                    type = NotificationType.RESTAURANT_APPROVED;
                    message = templates.restaurantApproved(name);
                }
                case "REJECTED" -> {
                    type = NotificationType.RESTAURANT_REJECTED;
                    message = templates.restaurantRejected(name, event.getReason());
                }
                case "ACTIVE" -> {
                    type = NotificationType.RESTAURANT_LIVE;
                    message = templates.restaurantLive(name);
                }
                case "INACTIVE" -> {
                    type = NotificationType.RESTAURANT_DEACTIVATED;
                    message = templates.restaurantDeactivated(name, event.getReason());
                }
                case "SUSPENDED" -> {
                    type = NotificationType.RESTAURANT_SUSPENDED;
                    message = templates.restaurantSuspended(name, event.getReason());
                }
                default -> {
                    return; // a status added later: nothing to say yet
                }
            }
        } else {
            return; // PROFILE_UPDATED
        }
        save(event.getEventId(), type, NotificationChannel.EMAIL, event.getOwnerUserId(), event.getRestaurantId(), null,
                null, message, event.getRequestId());
    }

    /** Order progress for the customer; a new order for the restaurant. Cancellations: see onOrderCancelled. */
    @Transactional
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "OrderStatusChanged")) {
            return;
        }
        long orderId = event.getOrderId();
        String restaurant = event.getRestaurantName();
        switch (event.getStatus()) {
            case "PLACED" -> {
                toCustomer(event, NotificationType.ORDER_PLACED, templates.orderPlaced(orderId, restaurant),
                        NotificationChannel.EMAIL, NotificationChannel.PUSH);
                save(event.getEventId(), NotificationType.NEW_ORDER_FOR_RESTAURANT, NotificationChannel.PUSH, null,
                        event.getRestaurantId(), orderId, null, templates.newOrderForRestaurant(orderId), event.getRequestId());
            }
            case ACCEPTED -> toCustomer(event, NotificationType.ORDER_ACCEPTED,
                    templates.orderAccepted(orderId, restaurant), NotificationChannel.PUSH);
            case "PREPARING" -> toCustomer(event, NotificationType.ORDER_PREPARING,
                    templates.orderPreparing(orderId, restaurant), NotificationChannel.PUSH);
            case "READY_FOR_PICKUP" -> toCustomer(event, NotificationType.ORDER_READY,
                    templates.orderReady(orderId, restaurant), NotificationChannel.PUSH);
            case "OUT_FOR_DELIVERY" -> toCustomer(event, NotificationType.ORDER_OUT_FOR_DELIVERY,
                    templates.orderOutForDelivery(orderId), NotificationChannel.PUSH, NotificationChannel.SMS);
            case "DELIVERED" -> toCustomer(event, NotificationType.ORDER_DELIVERED,
                    templates.orderDelivered(orderId), NotificationChannel.EMAIL, NotificationChannel.PUSH);
            default -> {
                // PAYMENT_PENDING: nothing to tell yet. CANCELLED / REJECTED: OrderCancelled carries the reason.
            }
        }
    }

    /** The customer learns why (and about the refund); the restaurant stops preparing an order it had. */
    @Transactional
    public void onOrderCancelled(OrderCancelled event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "OrderCancelled")) {
            return;
        }
        long orderId = event.getOrderId();
        Message message = templates.orderCancelled(orderId, event.getFinalStatus(), event.getCancelledBy(),
                event.getReason(), "PAID".equals(event.getPaymentStatus()), event.getTotalAmount(), event.getCurrency());
        for (NotificationChannel channel : Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH)) {
            save(event.getEventId(), NotificationType.ORDER_CANCELLED, channel, event.getCustomerId(), null, orderId,
                    null, message, event.getRequestId());
        }
        if (!"RESTAURANT".equals(event.getCancelledBy())
                && STATUSES_THE_RESTAURANT_WORKS_ON.contains(event.getPreviousStatus())) {
            save(event.getEventId(), NotificationType.ORDER_CANCELLED_FOR_RESTAURANT, NotificationChannel.PUSH, null,
                    event.getRestaurantId(), orderId, null,
                    templates.orderCancelledForRestaurant(orderId, event.getReason()), event.getRequestId());
        }
    }

    /** Failed payments and completed refunds. A successful payment is announced as "order placed". */
    @Transactional
    public void onPaymentStatusChanged(PaymentStatusChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "PaymentStatusChanged")) {
            return;
        }
        long orderId = event.getOrderId();
        switch (event.getStatus()) {
            case "FAILED" -> save(event.getEventId(), NotificationType.PAYMENT_FAILED, NotificationChannel.PUSH,
                    event.getCustomerId(), null, orderId, null, templates.paymentFailed(orderId, event.getReason()),
                    event.getRequestId());
            case "REFUNDED" -> {
                Message message = templates.refundCompleted(orderId, event.getRefundedAmount(), event.getCurrency());
                for (NotificationChannel channel : Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH)) {
                    save(event.getEventId(), NotificationType.REFUND_COMPLETED, channel, event.getCustomerId(), null,
                            orderId, null, message, event.getRequestId());
                }
            }
            default -> {
                // SUCCESS: the order's PLACED notification says it all
            }
        }
    }

    /**
     * Delivery progress: the partner hears about an offer (and a cancelled order), the customer learns who brings
     * the food. Pickup and delivery reach the customer as order statuses (OUT_FOR_DELIVERY, DELIVERED).
     */
    @Transactional
    public void onDeliveryStatusChanged(DeliveryStatusChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "DeliveryStatusChanged")) {
            return;
        }
        long orderId = event.getOrderId();
        switch (event.getStatus()) {
            case "ASSIGNED" -> save(event.getEventId(), NotificationType.DELIVERY_OFFERED, NotificationChannel.PUSH,
                    event.getPartnerUserId(), null, orderId, null,
                    templates.deliveryOffered(orderId, event.getRestaurantName()), event.getRequestId());
            case ACCEPTED -> save(event.getEventId(), NotificationType.PARTNER_ON_THE_WAY, NotificationChannel.PUSH,
                    event.getCustomerId(), null, orderId, null, templates.partnerOnTheWay(orderId,
                            event.getPartnerName(), event.getVehicleType(), event.getRestaurantName()),
                    event.getRequestId());
            case "CANCELLED" -> tellPartnerAboutCancellation(event);
            default -> {
                // PENDING, PICKED_UP, DELIVERED: nothing more to say than the order statuses already do
            }
        }
    }

    /** Only a partner who had the delivery (offered, accepted or picked up) needs to hear it is off. */
    private void tellPartnerAboutCancellation(DeliveryStatusChanged event) {
        if (event.getPartnerUserId() == null || !PARTNER_STATUSES.contains(event.getPreviousStatus())) {
            return;
        }
        long orderId = event.getOrderId();
        save(event.getEventId(), NotificationType.DELIVERY_CANCELLED_FOR_PARTNER, NotificationChannel.PUSH,
                event.getPartnerUserId(), null, orderId, null,
                templates.deliveryCancelledForPartner(orderId, "PICKED_UP".equals(event.getPreviousStatus())),
                event.getRequestId());
    }

    private void toCustomer(OrderStatusChanged event, NotificationType type, Message message,
                            NotificationChannel... channels) {
        for (NotificationChannel channel : channels) {
            save(event.getEventId(), type, channel, event.getCustomerId(), null, event.getOrderId(), null, message,
                    event.getRequestId());
        }
    }

    private void save(String eventId, NotificationType type, NotificationChannel channel, Long recipientUserId,
                      Long restaurantId, Long orderId, String address, Message message, String requestId) {
        Instant now = clock.instant();
        notificationRepository.save(Notification.pending(eventId, type, channel, recipientUserId, restaurantId, orderId,
                address, message.subject(), message.body(), requestId, now));
        log.info("Notification queued. type={} channel={} recipientUserId={} restaurantId={} orderId={}",
                type, channel, recipientUserId, restaurantId, orderId);
    }
}
