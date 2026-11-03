package com.fooddelivery.notificationservice.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fooddelivery.notificationservice.config.AppProperties;
import com.fooddelivery.notificationservice.dto.request.NotificationSearchCriteria;
import com.fooddelivery.notificationservice.dto.response.AdminNotificationResponse;
import com.fooddelivery.notificationservice.dto.response.AuditLogResponse;
import com.fooddelivery.notificationservice.dto.response.NotificationSummaryResponse;
import com.fooddelivery.notificationservice.dto.response.PageResponse;
import com.fooddelivery.notificationservice.entity.AuditAction;
import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import com.fooddelivery.notificationservice.exception.ConflictException;
import com.fooddelivery.notificationservice.exception.ResourceNotFoundException;
import com.fooddelivery.notificationservice.mapper.NotificationMapper;
import com.fooddelivery.notificationservice.repository.NotificationRepository;
import com.fooddelivery.notificationservice.repository.NotificationSpecifications;
import com.fooddelivery.notificationservice.repository.NotificationTotals;
import com.fooddelivery.notificationservice.validation.SortValidator;
import com.fooddelivery.notificationservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform admins monitoring notifications and resending the ones that could not be delivered. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "sentAt", "status", "type", "channel");
    private static final Set<NotificationStatus> RESENDABLE = Set.of(NotificationStatus.FAILED, NotificationStatus.SKIPPED);

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final AdminAuditService adminAuditService;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationResponse> search(NotificationSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        BusinessDateRange range = BusinessDateRange.of(criteria.fromDate(), criteria.toDate(), businessZone());

        List<Specification<Notification>> conditions = new ArrayList<>();
        if (criteria.recipientUserId() != null) {
            conditions.add(NotificationSpecifications.hasRecipient(criteria.recipientUserId()));
        }
        if (criteria.orderId() != null) {
            conditions.add(NotificationSpecifications.forOrder(criteria.orderId()));
        }
        if (criteria.restaurantId() != null) {
            conditions.add(NotificationSpecifications.forRestaurant(criteria.restaurantId()));
        }
        if (criteria.type() != null) {
            conditions.add(NotificationSpecifications.hasType(criteria.type()));
        }
        if (criteria.channel() != null) {
            conditions.add(NotificationSpecifications.hasChannel(criteria.channel()));
        }
        if (criteria.status() != null) {
            conditions.add(NotificationSpecifications.hasStatus(criteria.status()));
        }
        if (range.from() != null) {
            conditions.add(NotificationSpecifications.createdFrom(range.from()));
        }
        if (range.before() != null) {
            conditions.add(NotificationSpecifications.createdBefore(range.before()));
        }

        return PageResponse.from(notificationRepository.findAll(Specification.allOf(conditions), pageable)
                .map(notificationMapper::toAdminResponse));
    }

    @Transactional(readOnly = true)
    public AdminNotificationResponse getNotification(Long notificationId) {
        return notificationMapper.toAdminResponse(findNotification(notificationId));
    }

    /** FAILED or SKIPPED (for example after the user added a phone number): try again with all attempts. */
    @Transactional
    public AdminNotificationResponse resend(Long notificationId, String reason, CallerInfo admin) {
        Notification notification = findNotification(notificationId);
        NotificationStatus oldStatus = notification.getStatus();
        if (!RESENDABLE.contains(oldStatus)) {
            throw new ConflictException("Only FAILED or SKIPPED notifications can be resent (this one is " + oldStatus + ")");
        }
        notification.resend(clock.instant());
        adminAuditService.recordAction(admin, AuditAction.NOTIFICATION_RESENT, AdminAuditService.ENTITY_TYPE_NOTIFICATION,
                notificationId, oldStatus.name(), NotificationStatus.PENDING.name(), reason);
        log.info("Notification queued again by admin. notificationId={} adminUserId={}", notificationId, admin.userId());
        notificationRepository.flush();
        return notificationMapper.toAdminResponse(notification);
    }

    /** Totals per status and per channel for a period (default: today). */
    @Transactional(readOnly = true)
    public NotificationSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(clock.withZone(businessZone()));
        LocalDate from = fromDate != null ? fromDate : today;
        LocalDate to = toDate != null ? toDate : today;
        BusinessDateRange range = BusinessDateRange.of(from, to, businessZone());

        Map<NotificationStatus, Long> byStatus = new EnumMap<>(NotificationStatus.class);
        Map<NotificationChannel, Long> byChannel = new EnumMap<>(NotificationChannel.class);
        long total = 0;
        for (NotificationTotals totals : notificationRepository.countByStatusAndChannel(range.from(), range.before())) {
            byStatus.merge(totals.getStatus(), totals.getTotal(), Long::sum);
            byChannel.merge(totals.getChannel(), totals.getTotal(), Long::sum);
            total += totals.getTotal();
        }
        return new NotificationSummaryResponse(from, to, total, byStatus, byChannel);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Long notificationId, Pageable pageable) {
        findNotification(notificationId);
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_NOTIFICATION, notificationId, pageable);
    }

    private Notification findNotification(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
    }

    private ZoneId businessZone() {
        return ZoneId.of(appProperties.businessTimeZone());
    }
}
