package com.fooddelivery.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.notificationservice.channel.NotificationSender;
import com.fooddelivery.notificationservice.channel.NotificationSenderFactory;
import com.fooddelivery.notificationservice.channel.OutgoingMessage;
import com.fooddelivery.notificationservice.channel.PushSender;
import com.fooddelivery.notificationservice.channel.SmsSender;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient.RestaurantSummary;
import com.fooddelivery.notificationservice.client.UserServiceClient;
import com.fooddelivery.notificationservice.client.UserServiceClient.UserContact;
import com.fooddelivery.notificationservice.config.AppProperties;
import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.entity.NotificationType;
import com.fooddelivery.notificationservice.exception.RemoteServiceException;
import com.fooddelivery.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final List<Duration> DELAYS = List.of(Duration.ofSeconds(10), Duration.ofMinutes(1));

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private RestaurantServiceClient restaurantServiceClient;
    @Mock
    private NotificationSender emailSender;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(emailSender.channel()).thenReturn(NotificationChannel.EMAIL);
        lenient().when(emailSender.addressOf(any())).thenAnswer(invocation ->
                Optional.ofNullable(invocation.<UserContact>getArgument(0).email()));
        NotificationSenderFactory senders =
                new NotificationSenderFactory(List.of(emailSender, new SmsSender(), new PushSender()));
        AppProperties properties = new AppProperties("Asia/Kolkata", null, null,
                new AppProperties.Notifications("Food Delivery <no-reply@test>", true, 20, 3, DELAYS, Duration.ofMinutes(2)));
        dispatcher = new NotificationDispatcher(notificationRepository, senders, userServiceClient, restaurantServiceClient,
                properties, Clock.fixed(NOW, ZoneOffset.UTC), new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void emailIsSentToTheAddressUserServiceKnowsWithAGreeting() {
        Notification notification = givenDue(notification(NotificationChannel.EMAIL, 7L, null));
        when(userServiceClient.findUser(7L)).thenReturn(Optional.of(contact(7L, "priya@example.com", null, "ACTIVE")));

        dispatcher.dispatchDue();

        ArgumentCaptor<OutgoingMessage> message = ArgumentCaptor.forClass(OutgoingMessage.class);
        verify(emailSender).send(message.capture());
        assertThat(message.getValue().address()).isEqualTo("priya@example.com");
        assertThat(message.getValue().recipientName()).isEqualTo("Priya Sharma");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(NOW);
        assertThat(notification.getAttempts()).isEqualTo(1);
    }

    @Test
    void aRestaurantNotificationGoesToItsOwner() {
        Notification notification = givenDue(notification(NotificationChannel.PUSH, null, 10L));
        when(restaurantServiceClient.findRestaurant(10L))
                .thenReturn(Optional.of(new RestaurantSummary(10L, 5L, "Spice Hub", "ACTIVE")));
        when(userServiceClient.findUser(5L)).thenReturn(Optional.of(contact(5L, "arjun@example.com", null, "ACTIVE")));

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getRecipientUserId()).isEqualTo(5L);
        assertThat(notification.getRecipientAddress()).isEqualTo("inbox:user-5");
    }

    @Test
    void whileUserServiceIsDownTheNotificationWaitsAndIsRetriedLater() {
        Notification notification = givenDue(notification(NotificationChannel.EMAIL, 7L, null));
        when(userServiceClient.findUser(7L))
                .thenThrow(new RemoteServiceException("user-service", new RuntimeException("Connection refused")));

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(notification.getLastError()).contains("user-service");
        verify(emailSender, never()).send(any());
    }

    @Test
    void afterTheLastAttemptItIsFailedForAnAdminToResend() {
        Notification notification = notification(NotificationChannel.EMAIL, 7L, null);
        notification.setAttempts(2); // the next attempt is the third and last
        givenDue(notification);
        when(userServiceClient.findUser(7L))
                .thenThrow(new RemoteServiceException("user-service", new RuntimeException("Connection refused")));

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isEqualTo(3);
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    void aTextMessageForAUserWithoutAPhoneIsSkipped() {
        Notification notification = givenDue(notification(NotificationChannel.SMS, 7L, null));
        when(userServiceClient.findUser(7L)).thenReturn(Optional.of(contact(7L, "priya@example.com", null, "ACTIVE")));

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(notification.getLastError()).contains("no address for SMS");
    }

    @Test
    void deactivatedUsersGetNothing() {
        Notification notification = givenDue(notification(NotificationChannel.EMAIL, 7L, null));
        when(userServiceClient.findUser(7L)).thenReturn(Optional.of(contact(7L, "priya@example.com", null, "INACTIVE")));

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(notification.getLastError()).isEqualTo("User 7 is deactivated");
        verify(emailSender, never()).send(any());
    }

    @Test
    void aUserThatNoLongerExistsGetsNothing() {
        Notification notification = givenDue(notification(NotificationChannel.EMAIL, 7L, null));
        when(userServiceClient.findUser(7L)).thenReturn(Optional.empty());

        dispatcher.dispatchDue();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(notification.getLastError()).isEqualTo("User 7 not found");
        verify(emailSender, never()).send(any());
    }

    @Test
    void theLastRetryDelayRepeats() {
        assertThat(NotificationDispatcher.retryDelay(1, DELAYS)).isEqualTo(Duration.ofSeconds(10));
        assertThat(NotificationDispatcher.retryDelay(2, DELAYS)).isEqualTo(Duration.ofMinutes(1));
        assertThat(NotificationDispatcher.retryDelay(7, DELAYS)).isEqualTo(Duration.ofMinutes(1));
    }

    private Notification givenDue(Notification notification) {
        when(notificationRepository.lockDue(eq(NotificationStatus.PENDING), eq(NOW), any())).thenReturn(List.of(notification));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        return notification;
    }

    private static Notification notification(NotificationChannel channel, Long userId, Long restaurantId) {
        Notification notification = Notification.pending("event-1", NotificationType.ORDER_PLACED, channel, userId,
                restaurantId, 500L, null, "Order #500 placed", "Spice Hub has received your order.", "req-1",
                NOW.minusSeconds(1));
        notification.setId(1L);
        return notification;
    }

    private static UserContact contact(Long id, String email, String phone, String status) {
        return new UserContact(id, id == 7L ? "Priya Sharma" : "Arjun Mehta", email, phone, "CUSTOMER", status);
    }
}
