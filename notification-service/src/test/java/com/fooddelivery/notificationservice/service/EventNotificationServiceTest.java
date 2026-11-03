package com.fooddelivery.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.events.user.UserRegistered;
import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.entity.NotificationType;
import com.fooddelivery.notificationservice.repository.NotificationRepository;
import com.fooddelivery.notificationservice.template.NotificationTemplates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Who is notified about which event, on which channels, with which text. */
@ExtendWith(MockitoExtension.class)
class EventNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private final List<Notification> saved = new ArrayList<>();
    private EventNotificationService service;

    @BeforeEach
    void setUp() {
        service = new EventNotificationService(notificationRepository, processedEventGuard, new NotificationTemplates(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(processedEventGuard.firstDelivery(anyString(), anyString())).thenReturn(true);
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    @Test
    void welcomeEmailAndTextMessageUseTheAddressesFromTheEvent() {
        service.onUserRegistered(userRegistered("+919876543210"));

        assertThat(saved).extracting(Notification::getType, Notification::getChannel, Notification::getRecipientAddress)
                .containsExactly(
                        tuple(NotificationType.WELCOME, NotificationChannel.EMAIL, "kavya@example.com"),
                        tuple(NotificationType.WELCOME, NotificationChannel.SMS, "+919876543210"));
        assertThat(saved.getFirst().getSubject()).isEqualTo("Welcome to Food Delivery, Kavya Rao!");
        assertThat(saved.getFirst().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getFirst().getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void noTextMessageWithoutAPhoneNumber() {
        service.onUserRegistered(userRegistered(null));

        assertThat(saved).extracting(Notification::getChannel).containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    void aRepeatedEventNotifiesNobodyAgain() {
        UserRegistered event = userRegistered(null);
        when(processedEventGuard.firstDelivery(event.getEventId(), "UserRegistered")).thenReturn(false);

        service.onUserRegistered(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectedRestaurantOwnerLearnsTheReason() {
        service.onRestaurantChanged(restaurantChanged("STATUS_CHANGED", "REJECTED", "License photo is unreadable"));

        assertThat(saved).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.RESTAURANT_REJECTED);
            assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(notification.getRecipientUserId()).isEqualTo(5L);
            assertThat(notification.getBody()).contains("Reason: License photo is unreadable.");
        });
    }

    @Test
    void profileEditsNotifyNobody() {
        service.onRestaurantChanged(restaurantChanged("PROFILE_UPDATED", "ACTIVE", null));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void placedOrderNotifiesTheCustomerAndTheRestaurant() {
        service.onOrderStatusChanged(orderStatusChanged("PLACED"));

        assertThat(saved).extracting(Notification::getType, Notification::getChannel,
                        Notification::getRecipientUserId, Notification::getRestaurantId)
                .containsExactly(
                        tuple(NotificationType.ORDER_PLACED, NotificationChannel.EMAIL, 7L, null),
                        tuple(NotificationType.ORDER_PLACED, NotificationChannel.PUSH, 7L, null),
                        // the owner is looked up when sending
                        tuple(NotificationType.NEW_ORDER_FOR_RESTAURANT, NotificationChannel.PUSH, null, 10L));
        assertThat(saved).allSatisfy(notification -> assertThat(notification.getOrderId()).isEqualTo(500L));
    }

    @Test
    void unpaidAndCancelledStatusesAreNotAnnouncedHere() {
        service.onOrderStatusChanged(orderStatusChanged("PAYMENT_PENDING"));
        service.onOrderStatusChanged(orderStatusChanged("CANCELLED"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void customerCancellingAnAcceptedPaidOrderHearsAboutTheRefundAndTheRestaurantStops() {
        service.onOrderCancelled(orderCancelled("CANCELLED", "CUSTOMER", "ACCEPTED", "PAID"));

        assertThat(saved).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.ORDER_CANCELLED, NotificationType.ORDER_CANCELLED,
                        NotificationType.ORDER_CANCELLED_FOR_RESTAURANT);
        assertThat(saved).filteredOn(notification -> notification.getType() == NotificationType.ORDER_CANCELLED)
                .allSatisfy(notification -> assertThat(notification.getBody())
                        .contains("Your payment of INR 353.95 will be refunded"));
    }

    @Test
    void theRestaurantIsNotToldAboutItsOwnRejection() {
        service.onOrderCancelled(orderCancelled("REJECTED", "RESTAURANT", "PLACED", "PENDING"));

        assertThat(saved).extracting(Notification::getType)
                .containsOnly(NotificationType.ORDER_CANCELLED);
        assertThat(saved.getFirst().getSubject()).isEqualTo("Order #500 was declined");
        assertThat(saved.getFirst().getBody()).doesNotContain("refunded");
    }

    @Test
    void failedPaymentIsAPushAndASuccessfulOneIsSilent() {
        service.onPaymentStatusChanged(paymentStatusChanged("FAILED", "Card declined by the issuing bank"));
        service.onPaymentStatusChanged(paymentStatusChanged("SUCCESS", null));

        assertThat(saved).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.PAYMENT_FAILED);
            assertThat(notification.getChannel()).isEqualTo(NotificationChannel.PUSH);
            assertThat(notification.getBody()).contains("Card declined by the issuing bank");
        });
    }

    @Test
    void theOfferGoesToThePartnerAndTheAcceptanceToTheCustomer() {
        service.onDeliveryStatusChanged(deliveryStatusChanged("ASSIGNED", "PENDING"));
        service.onDeliveryStatusChanged(deliveryStatusChanged("ACCEPTED", "ASSIGNED"));
        service.onDeliveryStatusChanged(deliveryStatusChanged("PICKED_UP", "ACCEPTED"));

        assertThat(saved).extracting(Notification::getType, Notification::getChannel, Notification::getRecipientUserId)
                .containsExactly(
                        tuple(NotificationType.DELIVERY_OFFERED, NotificationChannel.PUSH, 77L),
                        tuple(NotificationType.PARTNER_ON_THE_WAY, NotificationChannel.PUSH, 7L));
        assertThat(saved.getFirst().getBody()).contains("Pick up order #500 at Spice Hub");
        assertThat(saved.getLast().getBody())
                .isEqualTo("Ravi Kumar (motorcycle) is on the way to Spice Hub to pick up your order.");
    }

    @Test
    void aPartnerWhoHasTheFoodIsAskedToTakeItBackWhenTheOrderIsCancelled() {
        service.onDeliveryStatusChanged(deliveryStatusChanged("CANCELLED", "PICKED_UP"));
        service.onDeliveryStatusChanged(DeliveryStatusChanged.newBuilder(deliveryStatusChanged("CANCELLED", "PENDING"))
                .setPartnerUserId(null).build());

        assertThat(saved).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.DELIVERY_CANCELLED_FOR_PARTNER);
            assertThat(notification.getRecipientUserId()).isEqualTo(77L);
            assertThat(notification.getBody()).contains("take the food back to the restaurant");
        });
    }

    private static DeliveryStatusChanged deliveryStatusChanged(String status, String previousStatus) {
        return DeliveryStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setDeliveryId(40L).setOrderId(500L).setCustomerId(7L).setRestaurantId(10L).setRestaurantName("Spice Hub")
                .setStatus(status).setPreviousStatus(previousStatus)
                .setPartnerUserId(77L).setPartnerName("Ravi Kumar").setVehicleType("MOTORCYCLE")
                .build();
    }

    private static UserRegistered userRegistered(String phone) {
        return UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setUserId(42L).setFullName("Kavya Rao").setEmail("kavya@example.com").setPhone(phone)
                .setRole("CUSTOMER")
                .build();
    }

    private static RestaurantChanged restaurantChanged(String changeType, String status, String reason) {
        return RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setChangeType(changeType).setRestaurantId(10L).setVersion(2L).setOwnerUserId(5L)
                .setName("Spice Hub").setCity("Bengaluru").setStatus(status).setReason(reason)
                .build();
    }

    private static OrderStatusChanged orderStatusChanged(String status) {
        return OrderStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setOrderId(500L).setCustomerId(7L).setRestaurantId(10L).setRestaurantName("Spice Hub")
                .setStatus(status).setChangedBy("SYSTEM")
                .build();
    }

    private static OrderCancelled orderCancelled(String finalStatus, String cancelledBy, String previousStatus,
                                                 String paymentStatus) {
        return OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setOrderId(500L).setCustomerId(7L).setRestaurantId(10L)
                .setFinalStatus(finalStatus).setPreviousStatus(previousStatus).setCancelledBy(cancelledBy)
                .setReason("Kitchen closed early").setPaymentMethod("UPI").setPaymentStatus(paymentStatus)
                .setTotalAmount(new BigDecimal("353.95")).setCurrency("INR")
                .build();
    }

    private static PaymentStatusChanged paymentStatusChanged(String status, String reason) {
        return PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(NOW)
                .setPaymentId(900L).setOrderId(500L).setCustomerId(7L).setStatus(status).setMethod("CARD")
                .setAmount(new BigDecimal("353.95")).setRefundedAmount(new BigDecimal("0.00")).setCurrency("INR")
                .setReason(reason)
                .build();
    }
}
