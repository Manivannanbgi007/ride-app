package com.fooddelivery.notificationservice.service;

import java.time.Clock;
import java.util.List;

import com.fooddelivery.notificationservice.dto.response.NotificationResponse;
import com.fooddelivery.notificationservice.dto.response.PageResponse;
import com.fooddelivery.notificationservice.dto.response.UnreadCountResponse;
import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.exception.ResourceNotFoundException;
import com.fooddelivery.notificationservice.mapper.NotificationMapper;
import com.fooddelivery.notificationservice.repository.NotificationRepository;
import com.fooddelivery.notificationservice.validation.SortValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's in-app inbox: their delivered PUSH notifications. Works for every role (customers, restaurant
 * owners, delivery partners); a user only ever sees their own notifications.
 */
@Service
@RequiredArgsConstructor
public class InboxService {

    private static final List<String> SORTABLE_FIELDS = List.of("sentAt", "createdAt");

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getInbox(Long userId, boolean unreadOnly, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        return PageResponse.from(notificationRepository.findAll(inboxOf(userId, unreadOnly), pageable)
                .map(notificationMapper::toInboxEntry));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse countUnread(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByRecipientUserIdAndChannelAndStatusAndReadAtIsNull(
                userId, NotificationChannel.PUSH, NotificationStatus.SENT));
    }

    /** Idempotent: reading an already read notification keeps the first read time. */
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
                .filter(found -> found.getChannel() == NotificationChannel.PUSH)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
        return notificationMapper.toInboxEntry(notification);
    }

    @Transactional
    public UnreadCountResponse markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, NotificationChannel.PUSH, NotificationStatus.SENT, clock.instant());
        return new UnreadCountResponse(0);
    }

    /** The inbox: a user's delivered PUSH notifications, optionally only the unread ones. */
    private static Specification<Notification> inboxOf(Long userId, boolean unreadOnly) {
        return (root, query, cb) -> {
            var conditions = cb.and(
                    cb.equal(root.get("recipientUserId"), userId),
                    cb.equal(root.get("channel"), NotificationChannel.PUSH),
                    cb.equal(root.get("status"), NotificationStatus.SENT));
            return unreadOnly ? cb.and(conditions, cb.isNull(root.get("readAt"))) : conditions;
        };
    }
}
