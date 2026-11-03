package com.fooddelivery.chatservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One plain-text message. Never edited or deleted; only {@code readAt} is set later, when the other side reads it
 * (by a bulk update, see MessageRepository.markRead).
 */
@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Long conversationId;

    @Column(name = "client_message_id", nullable = false, length = 36, updatable = false)
    private String clientMessageId;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private Long senderUserId;

    @Column(name = "sender_name", nullable = false, length = 120, updatable = false)
    private String senderName;

    @Column(name = "sender_role", nullable = false, length = 30, updatable = false)
    private String senderRole;

    @Column(name = "from_staff", nullable = false, updatable = false)
    private boolean fromStaff;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the other side read it; null while unread. */
    @Column(name = "read_at")
    private Instant readAt;

    public Message(Long conversationId, String clientMessageId, Long senderUserId, String senderName,
                   String senderRole, boolean fromStaff, String body, Instant createdAt) {
        this.conversationId = conversationId;
        this.clientMessageId = clientMessageId;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.fromStaff = fromStaff;
        this.body = body;
        this.createdAt = createdAt;
    }
}
