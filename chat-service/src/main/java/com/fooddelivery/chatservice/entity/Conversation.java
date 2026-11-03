package com.fooddelivery.chatservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The support conversation of one participant (a customer, restaurant owner or delivery partner). The other side is
 * the support team: all platform admins share it. Name and role are snapshots taken when it was opened. The last
 * message and the two unread counters are kept here, so the inbox needs no query on the messages.
 * Callers change it only through the methods below, so the counters can never drift from the messages.
 */
@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_user_id", nullable = false, updatable = false)
    private Long participantUserId;

    @Column(name = "participant_name", nullable = false, length = 120, updatable = false)
    private String participantName;

    @Column(name = "participant_role", nullable = false, length = 30, updatable = false)
    private String participantRole;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "last_message_from_staff")
    private Boolean lastMessageFromStaff;

    /** Participant messages that no admin has read yet. */
    @Column(name = "unread_for_staff", nullable = false)
    private int unreadForStaff;

    /** Support messages that the participant has not read yet. */
    @Column(name = "unread_for_participant", nullable = false)
    private int unreadForParticipant;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** The time comes from the injected Clock (not from the entity), so tests can use a fixed time. */
    public Conversation(Long participantUserId, String participantName, String participantRole, Instant now) {
        this.participantUserId = participantUserId;
        this.participantName = participantName;
        this.participantRole = participantRole;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void recordMessage(boolean fromStaff, String preview, Instant now) {
        this.lastMessageAt = now;
        this.lastMessagePreview = preview;
        this.lastMessageFromStaff = fromStaff;
        if (fromStaff) {
            unreadForParticipant++;
        } else {
            unreadForStaff++;
        }
        this.updatedAt = now;
    }

    /** The support team read what the participant wrote (any admin opening it counts for all admins). */
    public void markReadByStaff(Instant now) {
        if (unreadForStaff != 0) {
            unreadForStaff = 0;
            updatedAt = now;
        }
    }

    /** The participant read what the support team wrote. */
    public void markReadByParticipant(Instant now) {
        if (unreadForParticipant != 0) {
            unreadForParticipant = 0;
            updatedAt = now;
        }
    }

    /** Whether the other side has read the last message (false when there is none yet). */
    public boolean isLastMessageRead() {
        if (lastMessageFromStaff == null) {
            return false;
        }
        return lastMessageFromStaff ? unreadForParticipant == 0 : unreadForStaff == 0;
    }
}
