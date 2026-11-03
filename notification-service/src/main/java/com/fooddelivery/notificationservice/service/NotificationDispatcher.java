package com.fooddelivery.notificationservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.notificationservice.channel.NotificationSender;
import com.fooddelivery.notificationservice.channel.NotificationSenderFactory;
import com.fooddelivery.notificationservice.channel.OutgoingMessage;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient;
import com.fooddelivery.notificationservice.client.RestaurantServiceClient.RestaurantSummary;
import com.fooddelivery.notificationservice.client.UserServiceClient;
import com.fooddelivery.notificationservice.client.UserServiceClient.UserContact;
import com.fooddelivery.notificationservice.config.AppProperties;
import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.repository.NotificationRepository;
import com.fooddelivery.notificationservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends PENDING notifications. For each one: find the recipient (the restaurant's owner, then the user's
 * contact details from user-service), deliver it through its channel, record the outcome.
 * <ul>
 *   <li>Claimed in a short transaction (SKIP LOCKED + a lease), then sent with no transaction open,
 *       so a slow mail server or user-service never holds database locks.</li>
 *   <li>A failure (user-service down, SMTP error) is retried after 10s, 1m, 5m, 15m; then FAILED.</li>
 *   <li>Nothing to send it to (no phone number, deactivated user) is SKIPPED, with the reason.</li>
 * </ul>
 * Delivery is at least once: if the service stops after sending but before recording, it is sent again
 * when the lease expires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final NotificationSenderFactory notificationSenderFactory;
    private final UserServiceClient userServiceClient;
    private final RestaurantServiceClient restaurantServiceClient;
    private final AppProperties appProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /** What happened when a notification was sent. */
    private record Outcome(NotificationStatus status, Long recipientUserId, String address, String error) {

        static Outcome sent(Long recipientUserId, String address) {
            return new Outcome(NotificationStatus.SENT, recipientUserId, address, null);
        }

        static Outcome skipped(String reason) {
            return new Outcome(NotificationStatus.SKIPPED, null, null, reason);
        }

        static Outcome retry(String error) {
            return new Outcome(NotificationStatus.PENDING, null, null, error);
        }
    }

    /** @return how many notifications were taken (fewer than the batch size: nothing more is due now) */
    public int dispatchDue() {
        AppProperties.Notifications settings = appProperties.notifications();
        List<Long> claimed = transactionTemplate.execute(transaction -> {
            Instant now = clock.instant();
            List<Notification> due = notificationRepository.lockDue(NotificationStatus.PENDING, now,
                    PageRequest.of(0, settings.batchSize()));
            for (Notification notification : due) {
                // The lease: no other dispatcher takes it while this one is sending
                notification.setAttempts(notification.getAttempts() + 1);
                notification.setNextAttemptAt(now.plus(settings.lease()));
            }
            return due.stream().map(Notification::getId).toList();
        });
        claimed.forEach(this::dispatch);
        return claimed.size();
    }

    private void dispatch(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow();
        // Log lines (and calls to other services) carry the request id of the request that caused it.
        if (notification.getRequestId() != null) {
            MDC.put(RequestIdFilter.MDC_REQUEST_ID, notification.getRequestId());
        }
        try {
            saveOutcome(notificationId, attemptDelivery(notification));
        } catch (OptimisticLockingFailureException e) {
            // An admin changed it at the same moment (resend): their change wins.
            log.info("Notification changed while it was being sent. notificationId={}", notificationId);
        } finally {
            MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
        }
    }

    /** Any failure (another service down, the mail server refusing) means: try again later. */
    @SuppressWarnings("checkstyle:IllegalCatch") // a failed channel (any cause) is retried later
    private Outcome attemptDelivery(Notification notification) {
        try {
            return deliver(notification);
        } catch (RuntimeException e) {
            return Outcome.retry(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Outcome deliver(Notification notification) {
        NotificationSender sender = notificationSenderFactory.getSender(notification.getChannel());

        Long userId = notification.getRecipientUserId();
        if (userId == null) {
            // Addressed to a restaurant: its owner receives it
            Optional<RestaurantSummary> restaurant = restaurantServiceClient.findRestaurant(notification.getRestaurantId());
            if (restaurant.isEmpty()) {
                return Outcome.skipped("Restaurant " + notification.getRestaurantId() + " not found");
            }
            userId = restaurant.get().ownerUserId();
        }

        String address = notification.getRecipientAddress();
        String recipientName = null;
        if (address == null) {
            Optional<UserContact> contact = userServiceClient.findUser(userId);
            String user = "User " + userId;
            if (contact.isEmpty()) {
                return Outcome.skipped(user + " not found");
            }
            if (!contact.get().isActive()) {
                return Outcome.skipped(user + " is deactivated");
            }
            Optional<String> channelAddress = sender.addressOf(contact.get());
            if (channelAddress.isEmpty()) {
                return Outcome.skipped(user + " has no address for " + notification.getChannel());
            }
            address = channelAddress.get();
            recipientName = contact.get().fullName();
        }

        sender.send(new OutgoingMessage(address, recipientName, notification.getSubject(), notification.getBody()));
        return Outcome.sent(userId, address);
    }

    private void saveOutcome(Long notificationId, Outcome outcome) {
        transactionTemplate.executeWithoutResult(transaction -> {
            Notification notification = notificationRepository.findById(notificationId).orElseThrow();
            Instant now = clock.instant();
            switch (outcome.status()) {
                case SENT -> {
                    notification.setRecipientUserId(outcome.recipientUserId());
                    notification.markSent(outcome.address(), now);
                    log.info("Notification sent. notificationId={} type={} channel={} recipientUserId={}",
                            notificationId, notification.getType(), notification.getChannel(), outcome.recipientUserId());
                }
                case SKIPPED -> {
                    notification.markSkipped(outcome.error());
                    log.info("Notification skipped. notificationId={} reason={}", notificationId, outcome.error());
                }
                default -> retryOrFail(notification, outcome.error(), now);
            }
        });
    }

    private void retryOrFail(Notification notification, String error, Instant now) {
        AppProperties.Notifications settings = appProperties.notifications();
        if (notification.getAttempts() >= settings.maxAttempts()) {
            notification.markFailed(error);
            log.warn("Notification FAILED after {} attempts. notificationId={} error={}",
                    notification.getAttempts(), notification.getId(), error);
            return;
        }
        Duration delay = retryDelay(notification.getAttempts(), settings.retryDelays());
        notification.scheduleRetry(now.plus(delay), error);
        log.warn("Notification not sent, will retry. notificationId={} attempts={} retryIn={} error={}",
                notification.getId(), notification.getAttempts(), delay, error);
    }

    /** The delay before attempt n+1; the last configured delay repeats. */
    static Duration retryDelay(int attemptsSoFar, List<Duration> delays) {
        return delays.get(Math.min(attemptsSoFar, delays.size()) - 1);
    }
}
