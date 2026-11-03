package com.fooddelivery.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** What one admin chose for one conversation. The inbox is shared, pin and mute are personal. */
@Entity
@Table(name = "staff_conversation_settings")
@Getter
@NoArgsConstructor
public class StaffConversationSettings {

    @EmbeddedId
    private StaffConversationSettingsId id;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean muted;

    public StaffConversationSettings(Long staffUserId, Long conversationId) {
        this.id = new StaffConversationSettingsId(staffUserId, conversationId);
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }
}
