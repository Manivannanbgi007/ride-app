package com.fooddelivery.notificationservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.notificationservice.entity.Notification;
import com.fooddelivery.notificationservice.entity.NotificationChannel;
import com.fooddelivery.notificationservice.entity.NotificationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    /**
     * Notifications due for sending, locked until the transaction ends. Rows another dispatcher has
     * locked are skipped (SKIP LOCKED: lock timeout -2), so two instances never take the same one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select n from Notification n where n.status = :status and n.nextAttemptAt <= :now order by n.nextAttemptAt")
    List<Notification> lockDue(NotificationStatus status, Instant now, Pageable pageable);

    Page<Notification> findByRecipientUserId(Long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndChannel(Long recipientUserId, NotificationChannel channel, Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    long countByRecipientUserIdAndChannelAndStatusAndReadAtIsNull(Long recipientUserId, NotificationChannel channel,
                                                                 NotificationStatus status);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipientUserId = :userId and n.channel = :channel and n.status = :status and n.readAt is null
            """)
    int markAllRead(Long userId, NotificationChannel channel, NotificationStatus status, Instant now);

    /** Counts per status and channel for a period, counted in the database. */
    @Query("""
            select n.status as status, n.channel as channel, count(n) as total
            from Notification n
            where n.createdAt >= :from and n.createdAt < :before
            group by n.status, n.channel
            """)
    List<NotificationTotals> countByStatusAndChannel(Instant from, Instant before);
}
